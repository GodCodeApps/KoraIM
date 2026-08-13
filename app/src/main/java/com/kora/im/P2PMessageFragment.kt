package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kora.imcore.constant.SessionType
import com.kora.imcore.IMClient
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:10:23
 * @Description:
 */
class P2PMessageFragment : Fragment() {
    private val peerAccount get() = requireArguments().getString(ARG_PEER).orEmpty()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val existingSessionId = IMClient.getP2PConversation(peerAccount)?.sessionId.orEmpty()
            childFragmentManager.beginTransaction().replace(
                R.id.fragment_container,
                TMessageFragment().apply {
                    val apply = Bundle().apply {
                    putInt("session_type", SessionType.P2P)
                    putString("session_id", existingSessionId)
                    putString("peer_id", peerAccount)
                    }
                    arguments=apply
                }
            ).commit()
        }
    }

    companion object {
        private const val ARG_PEER = "peer_account"
        fun newInstance(account: String) = P2PMessageFragment().apply {
            arguments = Bundle().apply { putString(ARG_PEER, account) }
        }
    }
}
