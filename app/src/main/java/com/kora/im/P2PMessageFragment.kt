package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kora.imcore.constant.SessionType

/**
 * Copyright 2026 GodCodeApps
 * @Author: pym
 * @Date: 2026/07/15:10:23
 * @Description:
 */
class P2PMessageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction().replace(
            R.id.fragment_container,
            TMessageFragment().apply {
                val apply = Bundle().apply {
                    putInt("session_type", SessionType.P2P)
                    putString("session_id", "session123456789")
                }
                arguments=apply
            }).commit()
    }
}
