package com.kora.im

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kora.im.chat.ChatActivity
import com.kora.imcore.constant.SessionType

class ContactTabFragment : Fragment() {

    private val currentAccount get() = requireArguments().getString(ARG_ACCOUNT).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View = inflater.inflate(R.layout.fragment_contact, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val peers = DemoUsers.accounts.filterNot { it == currentAccount }
        val rv = view.findViewById<RecyclerView>(R.id.rv_contacts)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        rv.adapter = ContactAdapter(peers) { account ->
            val intent = Intent(activity, ChatActivity::class.java)
            intent.putExtra("session_type", SessionType.P2P)
            intent.putExtra("peer_id", account)
            startActivity(intent)
        }
    }

    private inner class ContactAdapter(
        private val accounts: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val flAvatar: FrameLayout = itemView.findViewById(R.id.fl_avatar)
            val tvLetter: TextView    = itemView.findViewById(R.id.tv_avatar_letter)
            val tvName: TextView      = itemView.findViewById(R.id.tv_nickname)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        )

        override fun getItemCount() = accounts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val account = accounts[position]
            val user = DemoUsers.demoUser(account)
            val allAccounts = DemoUsers.accounts
            val colorIndex = allAccounts.indexOf(account).coerceIn(0, DemoUsers.avatarColorRes.lastIndex)
            val colorRes = DemoUsers.avatarColorRes[colorIndex]

            val bg = holder.flAvatar.background.mutate() as? GradientDrawable
                ?: GradientDrawable().also { it.shape = GradientDrawable.OVAL }
            bg.setColor(ContextCompat.getColor(requireContext(), colorRes))
            holder.flAvatar.background = bg

            holder.tvLetter.text = user?.nickname?.firstOrNull()?.toString() ?: "?"
            holder.tvName.text   = user?.nickname ?: account

            holder.itemView.setOnClickListener { onClick(account) }
        }
    }

    companion object {
        private const val ARG_ACCOUNT = "account"
        fun newInstance(account: String) = ContactTabFragment().apply {
            arguments = Bundle().apply { putString(ARG_ACCOUNT, account) }
        }
    }
}
