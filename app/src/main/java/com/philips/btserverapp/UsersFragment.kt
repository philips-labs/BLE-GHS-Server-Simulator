package com.philips.btserverapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.philips.btserver.R
import com.philips.btserver.databinding.FragmentUsersBinding
import com.philips.btserver.userdataservice.UserDataManager
import timber.log.Timber

class UsersFragment : Fragment(), AdapterView.OnItemSelectedListener {

    private var _binding: FragmentUsersBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCurrentUserSpinner()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentUserView()
    }

    private fun setupCurrentUserSpinner() {
        ArrayAdapter.createFromResource(
            context!!,
            R.array.users_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.currentUser.adapter = adapter
        }
        binding.currentUser.onItemSelectedListener = this
        updateCurrentUserView()
    }

    private fun updateCurrentUserView() {
        val currentUserIndex = UserDataManager.currentUserIndex
        binding.currentUser.setSelection(
            if (isUnknownUser(currentUserIndex)) unknownUserIndex else currentUserIndex - 1
        )
    }

    private val unknownUserIndex: Int get() = binding.currentUser.adapter.count - 1

    private fun isUnknownUser(userIndex: Int): Boolean {
        return userIndex == UserDataManager.UNDEFINED_USER_INDEX
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        //System.out.println("onItemSelected: ${parent.getItemAtPosition(pos)} pos: $pos ")
        UserDataManager.currentUserIndex = if (pos == unknownUserIndex) UserDataManager.UNDEFINED_USER_INDEX else pos + 1
        Timber.i("onItemSelected: ${parent.getItemAtPosition(pos)} pos: $pos  currentUserIndex: ${UserDataManager.currentUserIndex}")
    }

    // AdapterView Interface callback
    override fun onNothingSelected(parent: AdapterView<*>) {}

}