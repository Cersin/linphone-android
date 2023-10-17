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
package org.linphone.ui.main.chat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.databinding.StartChatFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.viewmodel.StartConversationViewModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.fragment.GenericAddressPickerFragment
import org.linphone.ui.main.history.adapter.ContactsAndSuggestionsListAdapter
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.utils.Event

@UiThread
class StartConversationFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Start Conversation Fragment]"
    }

    private lateinit var binding: StartChatFragmentBinding

    private lateinit var viewModel: StartConversationViewModel

    private lateinit var adapter: ContactsAndSuggestionsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = StartChatFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[StartConversationViewModel::class.java]
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        adapter = ContactsAndSuggestionsListAdapter(viewLifecycleOwner)
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.adapter = adapter

        adapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                handleClickOnContactModel(model)
            }
        }

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())

        viewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            val count = adapter.itemCount
            adapter.submitList(it)

            if (count == 0 && it.isNotEmpty()) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }

        viewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                Log.i(
                    "$TAG Chat room [${pair.second}] for local address [${pair.first}] has been created, navigating to it"
                )
                sharedViewModel.showConversationEvent.value = Event(pair)
                goBack()
            }
        }

        viewModel.chatRoomCreationErrorEvent.observe(viewLifecycleOwner) {
            it.consume { error ->
                Log.i("$TAG Chat room creation error, showing red toast")
                (requireActivity() as MainActivity).showRedToast(error, R.drawable.warning_circle)
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            val trimmed = filter.trim()
            viewModel.applyFilter(trimmed)
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            // Do not consume it!
            viewModel.updateGroupChatButtonVisibility()
        }
    }

    @WorkerThread
    override fun onAddressSelected(address: Address, friend: Friend) {
        if (viewModel.multipleSelectionMode.value == true) {
            val avatarModel = ContactAvatarModel(friend)
            val model = SelectedAddressModel(address, avatarModel) {
                viewModel.removeAddressModelFromSelection(it)
            }
            viewModel.addAddressModelToSelection(model)
        } else {
            viewModel.createOneToOneChatRoomWith(address)
        }
    }
}
