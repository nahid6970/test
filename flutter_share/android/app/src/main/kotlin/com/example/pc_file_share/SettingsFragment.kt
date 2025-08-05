package com.example.pc_file_share

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

class SettingsFragment : Fragment() {

    private lateinit var serverUrlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var fileUploadService: FileUploadService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        serverUrlEditText = view.findViewById(R.id.server_url_edit_text)
        saveButton = view.findViewById(R.id.save_button)
        fileUploadService = FileUploadService(requireContext())

        serverUrlEditText.setText(fileUploadService.getServerUrl())

        saveButton.setOnClickListener {
            val url = serverUrlEditText.text.toString()
            fileUploadService.setServerUrl(url)
        }

        return view
    }
}