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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatMediaFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.adapter.ConversationsFilesAdapter
import org.linphone.ui.main.chat.viewmodel.ConversationMediaListViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

@UiThread
class ConversationMediaListFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Media List Fragment]"
    }

    private lateinit var binding: ChatMediaFragmentBinding

    private lateinit var viewModel: ConversationMediaListViewModel

    private lateinit var adapter: ConversationsFilesAdapter

    private val args: ConversationMediaListFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsFilesAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatMediaFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationMediaListViewModel::class.java]
        binding.viewModel = viewModel

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, localSipUri, remoteSipUri)

        binding.mediaList.setHasFixedSize(true)
        val layoutManager = object : GridLayoutManager(requireContext(), 4) {
            override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                lp.width = width / spanCount
                return true
            }
        }
        binding.mediaList.layoutManager = layoutManager

        if (binding.mediaList.adapter != adapter) {
            binding.mediaList.adapter = adapter
        }

        binding.setBackClickListener {
            goBack()
        }

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }

        viewModel.mediaList.observe(viewLifecycleOwner) { items ->
            if (items != adapter.currentList || items.size != adapter.itemCount) {
                adapter.submitList(items)
                Log.i("$TAG Media list updated with [${items.size}] items")
            }
        }

        viewModel.openMediaEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG User clicked on file [${model.file}], let's display it in file viewer")
                goToFileViewer(model.file)
            }
        }
    }

    private fun goToFileViewer(path: String) {
        Log.i("$TAG Navigating to file viewer fragment with path [$path]")
        val extension = FileUtils.getExtensionFromFileName(path)
        val mime = FileUtils.getMimeTypeFromExtension(extension)

        val bundle = Bundle()
        bundle.apply {
            putString("localSipUri", viewModel.localSipUri)
            putString("remoteSipUri", viewModel.remoteSipUri)
            putString("path", path)
        }
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image, FileUtils.MimeType.Video -> {
                bundle.putBoolean("isMedia", true)
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW)
                val contentUri: Uri =
                    FileUtils.getPublicFilePath(requireContext(), path)
                intent.setDataAndType(contentUri, "file/$mime")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    requireContext().startActivity(intent)
                } catch (anfe: ActivityNotFoundException) {
                    Log.e("$TAG Can't open file [$path] in third party app: $anfe")
                    val message = getString(
                        R.string.toast_no_app_registered_to_handle_content_type_error
                    )
                    val icon = R.drawable.file
                    (requireActivity() as MainActivity).showRedToast(message, icon)
                }
            }
        }
    }
}
