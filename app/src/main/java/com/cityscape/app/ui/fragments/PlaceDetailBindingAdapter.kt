package com.cityscape.app.ui.fragments

import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.cityscape.app.ui.viewmodels.ExplainableRecommendation
import com.cityscape.app.ui.viewmodels.Factor

/**
 * Data binding adapters for displaying explainable recommendations
 */

@BindingAdapter("bindConfidenceScore")
fun bindConfidenceScore(textView: TextView, recommendation: ExplainableRecommendation?) {
    recommendation?.let {
        textView.text = it.confidence
        // Color based on confidence
        val color = when {
            it.confidence.replace("%", "").toDoubleOrNull() ?: 0.0 >= 80 -> "#4CAF50" // Green
            it.confidence.replace("%", "").toDoubleOrNull() ?: 0.0 >= 60 -> "#FFC107" // Yellow
            else -> "#F44336" // Red
        }
        textView.setTextColor(android.graphics.Color.parseColor(color))
    }
}

@BindingAdapter("bindReasoning")
fun bindReasoning(textView: TextView, recommendation: ExplainableRecommendation?) {
    textView.text = recommendation?.reasoning ?: "No reason provided"
}

@BindingAdapter("bindFactorProgress")
fun bindFactorProgress(progressBar: ProgressBar, factor: Factor?) {
    factor?.let {
        val percentage = it.score.replace("%", "").toIntOrNull() ?: 0
        progressBar.progress = percentage
    }
}

@BindingAdapter("bindFactorScore")
fun bindFactorScore(textView: TextView, factor: Factor?) {
    textView.text = factor?.score ?: "0%"
}

@BindingAdapter("bindTotalConfidence")
fun bindTotalConfidence(textView: TextView, explanation: String?) {
    textView.text = explanation ?: "0%"
}
