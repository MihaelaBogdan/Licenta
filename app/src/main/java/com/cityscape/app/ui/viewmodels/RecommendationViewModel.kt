package com.cityscape.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.cityscape.app.data.network.ApiService
import com.cityscape.app.data.models.*

data class ExplainableRecommendation(
    val id: String,
    val name: String,
    val address: String,
    val rating: Double,
    val reviews: Int,
    val type: String,
    val confidence: String,
    val reasoning: String,
    val explanation: ExplanationDetail,
    val scoreBreakdown: Map<String, String>
)

data class ExplanationDetail(
    val summary: String,
    val factors: List<Factor>,
    val total_confidence: String
)

data class Factor(
    val name: String,
    val score: String,
    val description: String
)

class RecommendationViewModel(private val apiService: ApiService) : ViewModel() {

    private val _recommendations = MutableStateFlow<List<ExplainableRecommendation>>(emptyList())
    val recommendations: StateFlow<List<ExplainableRecommendation>> = _recommendations

    private val _selectedRecommendation = MutableStateFlow<ExplainableRecommendation?>(null)
    val selectedRecommendation: StateFlow<ExplainableRecommendation?> = _selectedRecommendation

    private val _stats = MutableStateFlow<RecommendationStats?>(null)
    val stats: StateFlow<RecommendationStats?> = _stats

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun fetchRecommendations(
        userId: String,
        latitude: Double,
        longitude: Double,
        interests: List<String>,
        language: String = "ro"
    ) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val response = apiService.getExplainableRecommendations(
                    user_id = userId,
                    lat = latitude,
                    lng = longitude,
                    interests = interests,
                    language = language,
                    limit = 5
                )

                _recommendations.value = response.recommendations.map { rec ->
                    ExplainableRecommendation(
                        id = rec.id ?: "",
                        name = rec.name ?: "",
                        address = rec.address ?: "",
                        rating = rec.rating?.toDoubleOrNull() ?: 0.0,
                        reviews = rec.reviews ?: 0,
                        type = rec.type ?: "",
                        confidence = rec.confidence ?: "0%",
                        reasoning = rec.reasoning ?: "",
                        explanation = rec.explanation ?: ExplanationDetail(
                            summary = "",
                            factors = emptyList(),
                            total_confidence = "0%"
                        ),
                        scoreBreakdown = rec.score_breakdown ?: emptyMap()
                    )
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectRecommendation(recommendation: ExplainableRecommendation) {
        _selectedRecommendation.value = recommendation
    }

    fun fetchStats(userId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getRecommendationStats(userId)
                _stats.value = response
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateRecommendationStatus(recId: String, status: String) {
        viewModelScope.launch {
            try {
                apiService.updateRecommendationStatus(recId, mapOf("status" to status))
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun getFactorPercentage(factor: Factor): Int {
        return factor.score.replace("%", "").toIntOrNull() ?: 0
    }

    fun getConfidenceColor(confidence: String): Int {
        val percentage = confidence.replace("%", "").toDoubleOrNull() ?: 0.0
        return when {
            percentage >= 80 -> android.graphics.Color.GREEN
            percentage >= 60 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }
    }
}

data class RecommendationStats(
    val status: String,
    val user_id: String,
    val total_recommendations: Int,
    val visited_count: Int,
    val accepted_count: Int,
    val rejected_count: Int,
    val accuracy_rate: String,
    val avg_confidence: String
)
