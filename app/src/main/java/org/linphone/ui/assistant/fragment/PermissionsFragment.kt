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
package org.linphone.ui.assistant.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantPermissionsFragmentBinding
import org.linphone.ui.assistant.viewmodel.PermissionsViewModel

@UiThread
class PermissionsFragment : Fragment() {
    companion object {
        private const val TAG = "[Permissions Fragment]"
    }

    private lateinit var binding: AssistantPermissionsFragmentBinding

    private val viewModel: PermissionsViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach() {
            val permissionName = it.key
            val isGranted = it.value

            when (permissionName) {
                "READ_CONTACTS" -> {
                    viewModel.readContacts.value = isGranted
                }
                "POST_NOTIFICATIONS" -> {
                    viewModel.postNotifications.value = isGranted
                }
                "RECORD_AUDIO" -> {
                    viewModel.recordAudio.value = isGranted
                }
                "CAMERA" -> {
                    viewModel.accessCamera.value = isGranted
                }
                else -> {}
            }

            if (isGranted) {
                Log.i("Permission [$permissionName] is now granted")
            } else {
                Log.i("Permission [$permissionName] has been denied")
            }
        }

        checkIfAllPermissionsHaveBeenGranted()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantPermissionsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setGrantReadContactsClickListener {
            Log.i("$TAG Requesting READ_CONTACTS permission")
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }

        // TODO FIXME: use compat for older Androids
        binding.setGrantPostNotificationsClickListener {
            Log.i("$TAG Requesting POST_NOTIFICATIONS permission")
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        binding.setGrantRecordAudioClickListener {
            Log.i("$TAG Requesting RECORD_AUDIO permission")
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        binding.setGrantAccessCameraClickListener {
            Log.i("$TAG Requesting CAMERA permission")
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        binding.setSkipClickListener {
            Log.i("$TAG User clicked skip...")
            goToLoginFragment()
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.MANAGE_OWN_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.MANAGE_OWN_CALLS))
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.readContacts.value = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        // TODO FIXME: use compat for older Androids
        viewModel.postNotifications.value = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.recordAudio.value = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.accessCamera.value = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        checkIfAllPermissionsHaveBeenGranted()
    }

    private fun goToLoginFragment() {
        val action = PermissionsFragmentDirections.actionPermissionsFragmentToLoginFragment()
        findNavController().navigate(action)
    }

    private fun checkIfAllPermissionsHaveBeenGranted() {
        if (viewModel.readContacts.value == true && viewModel.postNotifications.value == true && viewModel.recordAudio.value == true && viewModel.accessCamera.value == true) {
            Log.i("$TAG All permissions are granted, continuing to login fragment")
            goToLoginFragment()
        }
    }
}