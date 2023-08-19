/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.contacts.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Friend
import org.linphone.core.FriendList.Status
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.NewOrEditNumberOrAddressModel
import org.linphone.utils.Event

class ContactNewOrEditViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Contact New/Edit View Model]"
    }

    private lateinit var friend: Friend

    val isEdit = MutableLiveData<Boolean>()

    val picturePath = MutableLiveData<String>()

    val firstName = MutableLiveData<String>()

    val lastName = MutableLiveData<String>()

    val sipAddresses = ArrayList<NewOrEditNumberOrAddressModel>()

    val phoneNumbers = ArrayList<NewOrEditNumberOrAddressModel>()

    val company = MutableLiveData<String>()

    val jobTitle = MutableLiveData<String>()

    val saveChangesEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val friendFoundEvent = MutableLiveData<Event<Boolean>>()

    val addNewNumberOrAddressFieldEvent = MutableLiveData<Event<NewOrEditNumberOrAddressModel>>()

    val removeNewNumberOrAddressFieldEvent = MutableLiveData<Event<NewOrEditNumberOrAddressModel>>()

    @UiThread
    fun findFriendByRefKey(refKey: String?) {
        coreContext.postOnCoreThread { core ->
            friend = if (refKey.isNullOrEmpty()) {
                core.createFriend()
            } else {
                coreContext.contactsManager.findContactById(refKey) ?: core.createFriend()
            }
            val exists = !friend.refKey.isNullOrEmpty()
            isEdit.postValue(exists)

            if (exists) {
                Log.i("$TAG Found friend [$friend] using ref key [$refKey]")
                val vCard = friend.vcard
                if (vCard != null) {
                    firstName.postValue(vCard.givenName)
                    lastName.postValue(vCard.familyName)
                } else {
                    // TODO ?
                }

                picturePath.postValue(friend.photo)

                for (address in friend.addresses) {
                    addSipAddress(address.asStringUriOnly())
                }
                for (number in friend.phoneNumbers) {
                    addPhoneNumber(number)
                }

                company.postValue(friend.organization)
                jobTitle.postValue(friend.jobTitle)
            } else if (refKey.orEmpty().isNotEmpty()) {
                Log.e("$TAG No friend found using ref key [$refKey]")
            }

            addSipAddress()
            addPhoneNumber()

            friendFoundEvent.postValue(Event(exists))
        }
    }

    @UiThread
    fun saveChanges() {
        coreContext.postOnCoreThread { core ->
            var status = Status.OK

            if (!::friend.isInitialized) {
                friend = core.createFriend()
            }

            if (isEdit.value == true) {
                friend.edit()
            }

            friend.name = "${firstName.value.orEmpty()} ${lastName.value.orEmpty()}"

            val vCard = friend.vcard
            if (vCard != null) {
                vCard.familyName = lastName.value
                vCard.givenName = firstName.value

                // TODO FIXME : doesn't work for newly created contact
                val picture = picturePath.value.orEmpty()
                if (picture.isNotEmpty()) {
                    friend.photo = picture
                }
            }

            friend.organization = company.value.orEmpty()
            friend.jobTitle = jobTitle.value.orEmpty()

            for (address in friend.addresses) {
                friend.removeAddress(address)
            }
            for (address in sipAddresses) {
                val data = address.value.value
                if (!data.isNullOrEmpty()) {
                    val parsedAddress = core.interpretUrl(data, true)
                    if (parsedAddress != null) {
                        friend.addAddress(parsedAddress)
                    }
                }
            }

            for (number in friend.phoneNumbers) {
                friend.removePhoneNumber(number)
            }
            for (number in phoneNumbers) {
                val data = number.value.value
                if (!data.isNullOrEmpty()) {
                    friend.addPhoneNumber(data)
                }
            }

            if (isEdit.value == false) {
                if (friend.vcard?.generateUniqueId() == true) {
                    friend.refKey = friend.vcard?.uid
                    Log.i(
                        "$TAG Newly created friend will have generated ref key [${friend.refKey}]"
                    )
                } else {
                    Log.e("$TAG Failed to generate a ref key using vCard's generateUniqueId()")
                    // TODO : generate unique ref key
                }
                status = core.defaultFriendList?.addFriend(friend) ?: Status.InvalidFriend
            } else {
                friend.done()
            }
            coreContext.contactsManager.notifyContactsListChanged()

            saveChangesEvent.postValue(
                Event(if (status == Status.OK) friend.refKey.orEmpty() else "")
            )
        }
    }

    @WorkerThread
    private fun addSipAddress(address: String = "", requestFieldToBeAddedInUi: Boolean = false) {
        val newModel = NewOrEditNumberOrAddressModel(address, true, {
            if (address.isEmpty()) {
                addSipAddress(requestFieldToBeAddedInUi = true)
            }
        }, { model ->
            removeModel(model)
        })
        sipAddresses.add(newModel)

        if (requestFieldToBeAddedInUi) {
            addNewNumberOrAddressFieldEvent.postValue(Event(newModel))
        }
    }

    @WorkerThread
    private fun addPhoneNumber(number: String = "", requestFieldToBeAddedInUi: Boolean = false) {
        val newModel = NewOrEditNumberOrAddressModel(number, false, {
            if (number.isEmpty()) {
                addPhoneNumber(requestFieldToBeAddedInUi = true)
            }
        }, { model ->
            removeModel(model)
        })
        phoneNumbers.add(newModel)

        if (requestFieldToBeAddedInUi) {
            addNewNumberOrAddressFieldEvent.postValue(Event(newModel))
        }
    }

    @UiThread
    private fun removeModel(model: NewOrEditNumberOrAddressModel) {
        if (model.isSip) {
            sipAddresses.remove(model)
        } else {
            phoneNumbers.remove(model)
        }
        removeNewNumberOrAddressFieldEvent.value = Event(model)
    }
}