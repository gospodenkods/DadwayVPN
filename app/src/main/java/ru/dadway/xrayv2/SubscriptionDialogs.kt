package ru.dadway.xrayv2

import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

object SubscriptionDialogs {
    fun show(activity: AppCompatActivity, onChanged: () -> Unit) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_subscriptions, null)
        val list = content.findViewById<LinearLayout>(R.id.subscriptionList)

        fun renderSources() {
            list.removeAllViews()
            val sources = SubscriptionStore.all(activity)
            if (sources.isEmpty()) {
                list.addView(TextView(activity).apply {
                    text = "Подписки не добавлены. Нажмите «Добавить подписку» ниже."
                    setTextColor(ContextCompat.getColor(activity, R.color.dadway_text_secondary))
                    textSize = 13f
                })
            }
            sources.forEach { source ->
                val item = activity.layoutInflater.inflate(R.layout.item_subscription, list, false)
                val toggle = item.findViewById<SwitchMaterial>(R.id.subscriptionSwitch)
                toggle.text = source.title
                toggle.isChecked = source.enabled
                toggle.setOnCheckedChangeListener { _, enabled ->
                    SubscriptionStore.setEnabled(activity, source.id, enabled)
                    onChanged()
                }
                item.findViewById<ImageButton>(R.id.deleteSubscriptionButton).setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Удалить подписку?")
                        .setMessage(source.url)
                        .setPositiveButton("Удалить") { _, _ ->
                            SubscriptionStore.remove(activity, source.id)
                            renderSources()
                            onChanged()
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
                list.addView(item)
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Подписки")
            .setView(content)
            .setPositiveButton("Готово", null)
            .create()

        content.findViewById<MaterialButton>(R.id.addSubscriptionButton).setOnClickListener {
            val input = EditText(activity).apply {
                hint = "https://example.com/subscription"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
            }
            val addDialog = AlertDialog.Builder(activity)
                .setTitle("Новая подписка")
                .setView(input)
                .setPositiveButton("Добавить", null)
                .setNegativeButton("Отмена", null)
                .create()
            addDialog.setOnShowListener {
                addDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    runCatching { SubscriptionStore.add(activity, input.text.toString()) }
                        .onSuccess {
                            addDialog.dismiss()
                            renderSources()
                            onChanged()
                        }
                        .onFailure { input.error = it.message ?: "Не удалось добавить подписку" }
                }
            }
            addDialog.show()
        }
        renderSources()
        dialog.show()
    }
}
