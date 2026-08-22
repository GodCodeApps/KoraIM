package com.kora.im

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import android.widget.ImageView
import com.bumptech.glide.Glide

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View = inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_users)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = UserAdapter(DemoUsers.accounts) { account ->
            (activity as? MainActivity)?.login(account)
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    private inner class UserAdapter(
        private val accounts: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivAvatar: ImageView  = itemView.findViewById(R.id.iv_avatar)
            val tvName: TextView     = itemView.findViewById(R.id.tv_nickname)
            val tvDesc: TextView     = itemView.findViewById(R.id.tv_desc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_login_user, parent, false)
        )

        override fun getItemCount() = accounts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val account = accounts[position]
            val user    = DemoUsers.demoUser(account)
            val colorRes = DemoUsers.avatarColorRes.getOrElse(position) { R.color.avatar_1 }

            // Tint avatar background (it's a oval shape drawable)
            val bg = holder.ivAvatar.background.mutate() as? GradientDrawable
                ?: GradientDrawable().also { it.shape = GradientDrawable.OVAL }
            bg.setColor(ContextCompat.getColor(requireContext(), colorRes))
            holder.ivAvatar.background = bg

            if (user?.avatarUrl?.isNotEmpty() == true) {
                Glide.with(holder.itemView.context)
                    .load(user.avatarUrl)
                    .circleCrop()
                    .into(holder.ivAvatar)
            } else {
                holder.ivAvatar.setImageDrawable(null)
            }

            holder.tvName.text   = user?.nickname ?: account
            holder.tvDesc.text   = user?.description ?: ""

            holder.itemView.setOnClickListener { onClick(account) }
        }
    }
}
