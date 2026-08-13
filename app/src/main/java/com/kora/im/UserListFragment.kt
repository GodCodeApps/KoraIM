package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class UserListFragment : Fragment() {
    private val currentAccount get() = requireArguments().getString(ARG_ACCOUNT).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_user_list, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        view.findViewById<TextView>(R.id.current_account).text =
            "当前登录：${DemoUsers.info(currentAccount)?.nickname}"
        val list = view.findViewById<LinearLayout>(R.id.user_list)
        DemoUsers.accounts.filterNot { it == currentAccount }.forEach { account ->
            list.addView(Button(requireContext()).apply {
                text = "与 ${DemoUsers.info(account)?.nickname} 单聊"
                isAllCaps = false
                setOnClickListener { (activity as MainActivity).openChat(account) }
            })
        }
        if (childFragmentManager.findFragmentById(R.id.conversation_fragment_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.conversation_fragment_container, DemoConversationListFragment())
                .commit()
        }
    }

    companion object {
        private const val ARG_ACCOUNT = "account"
        fun newInstance(account: String) = UserListFragment().apply {
            arguments = Bundle().apply { putString(ARG_ACCOUNT, account) }
        }
    }
}
