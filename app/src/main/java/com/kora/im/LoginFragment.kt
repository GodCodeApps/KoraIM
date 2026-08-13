package com.kora.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment

class LoginFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val group = view.findViewById<RadioGroup>(R.id.account_group)
        DemoUsers.accounts.forEachIndexed { index, account ->
            group.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = DemoUsers.info(account)?.nickname
                tag = account
                isChecked = index == 0
            })
        }
        view.findViewById<Button>(R.id.login_button).setOnClickListener {
            val selected = group.findViewById<RadioButton>(group.checkedRadioButtonId)
            (activity as MainActivity).login(selected.tag as String)
        }
    }
}
