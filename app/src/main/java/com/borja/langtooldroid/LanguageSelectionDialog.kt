package com.borja.langtooldroid

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.borja.langtooldroid.databinding.DialogLanguageSelectorBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LanguageSelectionDialog : BottomSheetDialogFragment() {

    var onLanguagesSelected: ((List<String>) -> Unit)? = null
    var initiallySelectedCodes: List<String> = emptyList()

    private var _binding: DialogLanguageSelectorBinding? = null
    private val binding get() = _binding!!
    private val selectedCodes = mutableSetOf<String>()
    
    // For Mother Tongue mode (single selection)
    var isSingleSelection = false
    var onSingleLanguageSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLanguageSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Init selection
        selectedCodes.clear()
        selectedCodes.addAll(initiallySelectedCodes)

        // Setup Adapter
        val allLanguages = if(isSingleSelection) LanguageRepository.languages.filter { it.code != "auto" } else LanguageRepository.languages
        val adapter = LanguageAdapter(allLanguages)
        binding.rvLanguages.adapter = adapter
        
        // Setup Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Buttons
        binding.btnApply.setOnClickListener {
            if (isSingleSelection) {
                 // Should have been handled by item click
                 dismiss()
            } else {
                val list = selectedCodes.toList()
                onLanguagesSelected?.invoke(if (list.isEmpty()) listOf("auto") else list)
                dismiss()
            }
        }
        
        if (isSingleSelection) binding.btnApply.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LanguageAdapter(private val originalList: List<Language>) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        private var filteredList = originalList.toList()

        override fun getItemCount(): Int = filteredList.size

        fun filter(query: String) {
            filteredList = if (query.isBlank()) {
                originalList
            } else {
                originalList.filter {
                    it.name.contains(query, ignoreCase = true) || 
                    it.nativeName.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvCode: TextView = itemView.findViewById(R.id.tvCode)
            val tvNative: TextView = itemView.findViewById(R.id.tvNativeName)
            val cbSelected: CheckBox = itemView.findViewById(R.id.cbSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lang = filteredList[position]
            holder.tvName.text = lang.name
            holder.tvCode.text = lang.code
            holder.tvNative.text = lang.nativeName

            if (isSingleSelection) {
                holder.cbSelected.visibility = View.GONE
                holder.itemView.setOnClickListener {
                    onSingleLanguageSelected?.invoke(lang.code)
                    dismiss()
                }
            } else {
                holder.cbSelected.visibility = View.VISIBLE
                
                // Logic for "auto": if selected, unselect others. If others selected, unselect auto.
                val isChecked = selectedCodes.contains(lang.code)
                holder.cbSelected.isChecked = isChecked
                
                holder.itemView.setOnClickListener {
                    toggleSelection(lang.code)
                    notifyDataSetChanged() // efficient enough for simple list
                }
            }
        }
        
        private fun toggleSelection(code: String) {
            if (code == "auto") {
                if (selectedCodes.contains("auto")) {
                    selectedCodes.remove("auto")
                } else {
                    selectedCodes.clear()
                    selectedCodes.add("auto")
                }
            } else {
                // If selecting normal lang, remove auto
                if (selectedCodes.contains("auto")) selectedCodes.remove("auto")
                
                if (selectedCodes.contains(code)) {
                    selectedCodes.remove(code)
                } else {
                    selectedCodes.add(code)
                }
            }
        }
    }
}
