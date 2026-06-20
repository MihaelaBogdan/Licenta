package com.cityscape.app.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cityscape.app.R
import com.cityscape.app.databinding.ActivityPlaceDetailBinding
import com.cityscape.app.ui.viewmodels.RecommendationViewModel
import com.cityscape.app.ui.viewmodels.ExplainableRecommendation
import kotlinx.coroutines.launch

/**
 * Activity that displays place details with explainable recommendations.
 * Shows confidence scores, reasoning, and factor breakdown.
 */
class PlaceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailBinding
    private lateinit var viewModel: RecommendationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlaceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel (you'll need to provide the apiService)
        // viewModel = RecommendationViewModel(ApiClient.apiService)

        // Get place ID from intent
        val placeId = intent.getStringExtra("place_id")
        val userId = intent.getStringExtra("user_id")
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)

        // Fetch recommendations if needed
        if (userId != null) {
            // viewModel.fetchRecommendations(
            //     userId = userId,
            //     latitude = latitude,
            //     longitude = longitude,
            //     interests = listOf("art", "history")
            // )

            // Observe recommendations
            // viewModel.selectedRecommendation.observe(this) { rec ->
            //     displayRecommendation(rec)
            // }
        }

        setupClickListeners()
    }

    private fun displayRecommendation(recommendation: ExplainableRecommendation) {
        // Show analysis section
        binding.aiAnalysisSection.visibility = View.VISIBLE

        // Display confidence score
        binding.confidenceScoreBadge.text = recommendation.confidence
        binding.confidenceScoreBadge.setTextColor(
            getConfidenceColor(recommendation.confidence)
        )

        // Display reasoning
        binding.aiReasonText.text = recommendation.reasoning

        // Display factors
        if (recommendation.explanation.factors.isNotEmpty()) {
            displayFactors(recommendation)
        }

        // Display total confidence
        binding.totalConfidence.text = recommendation.explanation.total_confidence
    }

    private fun displayFactors(recommendation: ExplainableRecommendation) {
        val factors = recommendation.explanation.factors

        if (factors.size >= 1) {
            setFactor(
                binding.factorInterestProgress,
                binding.factorInterest,
                factors[0]
            )
        }

        if (factors.size >= 2) {
            setFactor(
                binding.factorFreshnessProgress,
                binding.factorFreshness,
                factors[1]
            )
        }

        if (factors.size >= 3) {
            setFactor(
                binding.factorPopularityProgress,
                binding.factorPopularity,
                factors[2]
            )
        }

        if (factors.size >= 4) {
            setFactor(
                binding.factorLevelProgress,
                binding.factorLevel,
                factors[3]
            )
        }

        if (factors.size >= 5) {
            setFactor(
                binding.factorDiversityProgress,
                binding.factorDiversity,
                factors[4]
            )
        }
    }

    private fun setFactor(
        progressBar: ProgressBar,
        textView: TextView,
        factor: com.cityscape.app.ui.viewmodels.Factor
    ) {
        val percentage = factor.score.replace("%", "").toIntOrNull() ?: 0
        progressBar.progress = percentage
        textView.text = factor.score
    }

    private fun getConfidenceColor(confidence: String): Int {
        val percentage = confidence.replace("%", "").toDoubleOrNull() ?: 0.0
        return when {
            percentage >= 80 -> android.graphics.Color.GREEN
            percentage >= 60 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFavorite.setOnClickListener {
            // Handle favorite
        }

        binding.btnShare.setOnClickListener {
            // Handle share
        }
    }
}
