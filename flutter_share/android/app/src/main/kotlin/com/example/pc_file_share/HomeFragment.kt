package com.example.pc_file_share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var selectFilesButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fileUploadService: FileUploadService

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        selectFilesButton = view.findViewById(R.id.select_files_button)
        statusText = view.findViewById(R.id.status_text)
        progressBar = view.findViewById(R.id.progress_bar)
        fileUploadService = FileUploadService(requireContext())

        selectFilesButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            filePickerLauncher.launch(intent)
        }

        return view
    }

    private fun uploadFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            fileUploadService.uploadFile(uri, {
                CoroutineScope(Dispatchers.Main).launch {
                    progressBar.progress = (it * 100).toInt()
                }
            }, {
                CoroutineScope(Dispatchers.Main).launch {
                    statusText.text = it
                }
            })
        }
    }
}