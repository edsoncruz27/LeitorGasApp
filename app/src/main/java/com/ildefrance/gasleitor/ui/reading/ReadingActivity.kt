package com.ildefrance.gasleitor.ui.reading

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ildefrance.gasleitor.R
import com.ildefrance.gasleitor.data.model.ApartmentStatus
import com.ildefrance.gasleitor.data.repository.ReadingRepository
import com.ildefrance.gasleitor.databinding.ActivityReadingBinding
import com.ildefrance.gasleitor.ui.summary.SummaryActivity
import com.ildefrance.gasleitor.ui.date.DateConfirmActivity
import com.ildefrance.gasleitor.util.ApartmentHelper
import kotlinx.coroutines.launch
import java.util.Locale

class ReadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CYCLE_ID = "extra_cycle_id"
        const val EXTRA_MONTH = "extra_month"
        const val EXTRA_YEAR = "extra_year"
    }

    private lateinit var binding: ActivityReadingBinding
    private lateinit var repository: ReadingRepository
    private lateinit var adapter: ApartmentAdapter

    private var cycleId: Long = -1
    private var currentMonth: Int = 0
    private var currentYear: Int = 0
    private var selectedApartment: ApartmentStatus? = null
    private var apartmentList: MutableList<ApartmentStatus> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cycleId = intent.getLongExtra(EXTRA_CYCLE_ID, -1)
        currentMonth = intent.getIntExtra(EXTRA_MONTH, 1)
        currentYear = intent.getIntExtra(EXTRA_YEAR, 2024)

        repository = ReadingRepository(this)

        setupRecyclerView()
        setupInputPanel()
        setupButtons()
        loadApartments()
    }

    private fun setupRecyclerView() {
        adapter = ApartmentAdapter { aptStatus ->
            selectApartment(aptStatus)
        }
        binding.rvApartments.apply {
            layoutManager = LinearLayoutManager(this@ReadingActivity)
            adapter = this@ReadingActivity.adapter
        }
    }

    private fun setupInputPanel() {
        binding.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateInput()
            }
        })

        // Ao apertar "Enter" (ação "Concluído") no teclado numérico,
        // já salva a leitura sem precisar clicar em "Confirmar Leitura".
        binding.etValue.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                if (binding.btnConfirmReading.isEnabled) {
                    confirmReading()
                }
                true
            } else {
                false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etValue.windowToken, 0)
    }

    private fun validateInput() {
        val text = binding.etValue.text.toString().replace(",", ".")
        val value = text.toDoubleOrNull()
        val valid = text.isNotEmpty() && value != null && value >= 0 && selectedApartment != null
        binding.btnConfirmReading.isEnabled = valid
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnConfirmReading.isEnabled = false
        binding.btnConfirmReading.setOnClickListener {
            hideKeyboard()
            confirmReading()
        }

        binding.btnCancelSelection.setOnClickListener {
            clearSelection()
        }

        binding.btnFinishCycle.setOnClickListener {
            showFinishConfirmation()
        }
    }

    private fun loadApartments() {
        lifecycleScope.launch {
            val statusList = repository.getApartmentStatusList(cycleId)
            apartmentList.clear()
            apartmentList.addAll(statusList)
            adapter.submitList(apartmentList.toList())
            updateProgress()
            updateFinishButton()
        }
    }

    private fun selectApartment(aptStatus: ApartmentStatus) {
        selectedApartment = aptStatus
        binding.inputPanel.visibility = View.VISIBLE
        binding.tvSelectedApt.text = "Apto ${aptStatus.apartment} — ${aptStatus.floor}º Andar"

        // If already has a reading, pre-fill it (always with dot as decimal separator)
        if (aptStatus.isDone && aptStatus.reading != null) {
            binding.etValue.setText(String.format(Locale.US, "%.3f", aptStatus.reading))
        } else {
            binding.etValue.setText("")
        }
        binding.etValue.requestFocus()
        validateInput()
    }

    private fun clearSelection() {
        selectedApartment = null
        binding.inputPanel.visibility = View.GONE
        binding.etValue.setText("")
        binding.btnConfirmReading.isEnabled = false
    }

    private fun confirmReading() {
        val apt = selectedApartment ?: return
        val valueStr = binding.etValue.text.toString()
        val value = valueStr.replace(",", ".").toDoubleOrNull() ?: return

        lifecycleScope.launch {
            repository.saveReading(cycleId, apt.apartment, apt.floor, value)

            // Update local list
            val idx = apartmentList.indexOfFirst { it.apartment == apt.apartment }
            if (idx >= 0) {
                apartmentList[idx] = apartmentList[idx].copy(reading = value, isDone = true)
                adapter.submitList(apartmentList.toList())
            }

            updateProgress()
            updateFinishButton()
            clearSelection()

            val formattedValue = String.format(Locale("pt", "BR"), "%.3f", value)
            Snackbar.make(
                binding.root,
                "✓ Apto ${apt.apartment}: $formattedValue m³ salvo",
                Snackbar.LENGTH_SHORT
            ).show()

            // Auto-scroll to next unread apartment
            val nextUnread = apartmentList.indexOfFirst { !it.isDone }
            if (nextUnread >= 0) {
                binding.rvApartments.smoothScrollToPosition(nextUnread)
            }
        }
    }

    private fun updateProgress() {
        val done = apartmentList.count { it.isDone }
        val total = ApartmentHelper.getTotalCount()
        binding.tvProgress.text = "$done / $total lidos"
        binding.progressBar.progress = done
        binding.progressBar.max = total
    }

    private fun updateFinishButton() {
        val done = apartmentList.count { it.isDone }
        val total = ApartmentHelper.getTotalCount()
        binding.btnFinishCycle.isEnabled = done == total
        if (done == total) {
            binding.btnFinishCycle.alpha = 1.0f
        } else {
            binding.btnFinishCycle.alpha = 0.5f
        }
    }

    private fun showFinishConfirmation() {
        val done = apartmentList.count { it.isDone }
        val total = ApartmentHelper.getTotalCount()

        AlertDialog.Builder(this)
            .setTitle("Finalizar Leitura")
            .setMessage("Todos os $total apartamentos foram lidos.\n\nDeseja finalizar o ciclo de leitura e exportar a planilha?")
            .setPositiveButton("✓ Finalizar e Exportar") { _, _ ->
                finishCycle()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun finishCycle() {
        lifecycleScope.launch {
            repository.finishCycle(cycleId)
            val intent = Intent(this@ReadingActivity, SummaryActivity::class.java).apply {
                putExtra(SummaryActivity.EXTRA_CYCLE_ID, cycleId)
                putExtra(SummaryActivity.EXTRA_MONTH, currentMonth)
                putExtra(SummaryActivity.EXTRA_YEAR, currentYear)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Sair da leitura?")
            .setMessage("O progresso atual será salvo. Você pode continuar depois.")
            .setPositiveButton("Sair") { _, _ ->
                val intent = Intent(this, DateConfirmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Continuar", null)
            .show()
    }
}

// ─── Adapter ─────────────────────────────────────────────────────────────────

class ApartmentAdapter(
    private val onItemClick: (ApartmentStatus) -> Unit
) : RecyclerView.Adapter<ApartmentAdapter.ViewHolder>() {

    private var items: List<ApartmentStatus> = emptyList()
    private var lastFloor: Int = -1

    fun submitList(newList: List<ApartmentStatus>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apartment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val showDivider = position == 0 || items[position - 1].floor != item.floor
        holder.bind(item, showDivider)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: CardView = view.findViewById(R.id.card_apartment)
        private val tvApt: TextView = view.findViewById(R.id.tv_apt_number)
        private val tvFloor: TextView = view.findViewById(R.id.tv_floor)
        private val tvReading: TextView = view.findViewById(R.id.tv_reading_value)
        private val tvStatus: TextView = view.findViewById(R.id.tv_status)
        private val floorDivider: View = view.findViewById(R.id.floor_divider)
        private val tvFloorLabel: TextView = view.findViewById(R.id.tv_floor_label)

        fun bind(item: ApartmentStatus, showFloorDivider: Boolean) {
            val ctx = itemView.context

            if (showFloorDivider) {
                floorDivider.visibility = View.VISIBLE
                tvFloorLabel.visibility = View.VISIBLE
                tvFloorLabel.text = "${item.floor}º ANDAR"
            } else {
                floorDivider.visibility = View.GONE
                tvFloorLabel.visibility = View.GONE
            }

            tvApt.text = "Apto ${item.apartment}"
            tvFloor.text = "${item.floor}º andar"

            if (item.isDone && item.reading != null) {
                tvReading.text = String.format(Locale("pt", "BR"), "%.3f m³", item.reading)
                tvStatus.text = "✓ Lido"
                tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green_done))
                tvReading.visibility = View.VISIBLE
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_done))
            } else {
                tvReading.visibility = View.GONE
                tvStatus.text = "● Pendente"
                tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.orange_pending))
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_pending))
            }
        }
    }
}
