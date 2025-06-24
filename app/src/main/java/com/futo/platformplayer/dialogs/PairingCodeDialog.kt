package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs

class PairingCodeDialog(context: Context?, private val onSubmit: (code: String) -> Unit) : AlertDialog(context) {
    private lateinit var _editPairingCode: EditText
    private lateinit var _textError: TextView
    private lateinit var _buttonSubmit: LinearLayout
    private lateinit var _buttonCancel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_pairing_code, null))

        _editPairingCode = findViewById(R.id.edit_pairing_code)
        _textError = findViewById(R.id.text_error)
        _buttonSubmit = findViewById(R.id.button_submit)
        _buttonCancel = findViewById(R.id.button_cancel)

        setTitle("Enter Pairing Code")

        _buttonCancel.setOnClickListener {
            performDismiss()
        }

        _buttonSubmit.setOnClickListener {
            val code = _editPairingCode.text.toString().trim()
            if (code.isBlank()) {
                _textError.text = "Pairing code cannot be empty."
                _textError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            _textError.visibility = View.GONE
            onSubmit(code)
            performDismiss()
        }
    }

    override fun show() {
        super.show()

        _editPairingCode.text.clear()
        _textError.visibility = View.GONE

        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun performDismiss() {
        dismiss()
    }

    companion object {
        private val TAG = "PairingCodeDialog"
    }
}