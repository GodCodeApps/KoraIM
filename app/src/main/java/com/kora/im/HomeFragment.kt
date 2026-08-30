package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kora.im.chat.MessageFragment
import com.kora.imcore.IMClient
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val currentAccount get() = requireArguments().getString(ARG_ACCOUNT).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val user = DemoUsers.demoUser(currentAccount)
        view.findViewById<TextView>(R.id.tv_home_title).text = "KoraIM"
        view.findViewById<TextView>(R.id.tv_current_nickname).text = user?.nickname ?: currentAccount

        // Switch tab 按钮
        view.findViewById<TextView>(R.id.tv_logout).setOnClickListener {
            (activity as? MainActivity)?.showLogin()
        }

        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottom_nav)
        observeUnreadBadge(bottomNav)

        // Default tab: conversations
        if (childFragmentManager.findFragmentById(R.id.home_content) == null) {
            showTab(R.id.tab_chat)
        }

        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
    }

    private fun observeUnreadBadge(bottomNav: BottomNavigationView) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                IMClient.observeTotalUnreadCount().collect { count ->
                    if (count <= 0) {
                        bottomNav.removeBadge(R.id.tab_chat)
                    } else {
                        bottomNav.getOrCreateBadge(R.id.tab_chat).apply {
                            isVisible = true
                            number = count
                            maxCharacterCount = 3 // Material displays counts above 99 as 99+.
                        }
                    }
                }
            }
        }
    }

    private fun showTab(tabId: Int) {
        val fragment: Fragment = when (tabId) {
            R.id.tab_chat     -> MessageFragment()
            R.id.tab_contacts -> ContactTabFragment.newInstance(currentAccount)
            else              -> MessageFragment()
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.home_content, fragment)
            .commit()
    }

    companion object {
        private const val ARG_ACCOUNT = "account"
        fun newInstance(account: String) = HomeFragment().apply {
            arguments = Bundle().apply { putString(ARG_ACCOUNT, account) }
        }
    }
}
