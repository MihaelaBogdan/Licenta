"""
LLM Explanation Faithfulness Evaluation
========================================

Experiment: Verificarea consistenței explicațiilor unui LLM local
cu predicțiile și feature importance-urile unui model de regresie.

Pipeline:
1. Încarcă dataset (California Housing)
2. Antrenează Random Forest
3. Calculează SHAP values (adevărul matematic)
4. Folosește Ollama + Llama pentru generare explicații în limbaj natural
5. Evaluează consistența (cât halucinează LLM-ul)
"""

import os
import json
import numpy as np
import pandas as pd
import pickle
from pathlib import Path
from typing import Dict, List, Tuple
import warnings
warnings.filterwarnings('ignore')

# Models & ML
from sklearn.datasets import fetch_california_housing
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import r2_score, mean_absolute_error, mean_squared_error
import shap

# LLM
import requests
import re


# ============================================================================
# 1. DATA & MODEL
# ============================================================================

def load_and_prepare_data():
    """Încarcă California Housing dataset."""
    print("📊 Loading California Housing dataset...")
    housing = fetch_california_housing()

    df = pd.DataFrame(
        housing.data,
        columns=[
            'MedInc', 'HouseAge', 'AveRooms', 'AveBedrms',
            'Population', 'AveOccup', 'Latitude', 'Longitude'
        ]
    )
    df['Price'] = housing.target

    print(f"✓ Dataset shape: {df.shape}")
    print(f"✓ Features: {df.columns.tolist()}")
    return df


def train_model(df: pd.DataFrame, test_size=0.2, random_state=42):
    """Antrenează Random Forest regressor."""
    print("\n🤖 Training Random Forest model...")

    X = df.drop('Price', axis=1)
    y = df['Price']

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state
    )

    model = RandomForestRegressor(
        n_estimators=100,
        max_depth=15,
        min_samples_split=5,
        random_state=random_state,
        n_jobs=-1
    )

    model.fit(X_train, y_train)

    # Evalare
    y_pred = model.predict(X_test)
    r2 = r2_score(y_test, y_pred)
    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))

    print(f"✓ Model trained")
    print(f"  - R² Score: {r2:.4f}")
    print(f"  - MAE: ${mae*100:.2f}k")
    print(f"  - RMSE: ${rmse*100:.2f}k")

    return model, X_test, y_test, X_train.columns


def compute_shap_values(model, X_train, X_test, feature_names):
    """Calculează SHAP values — adevărul matematic al feature importance."""
    print("\n📈 Computing SHAP values (ground truth feature importance)...")

    # SHAP Explainer
    explainer = shap.TreeExplainer(model)
    shap_values = explainer.shap_values(X_test)
    base_value = explainer.expected_value

    # Media valorilor absolute SHAP per feature (importance global)
    if isinstance(shap_values, list):  # Multi-output
        shap_values = shap_values[0]

    feature_importance = np.abs(shap_values).mean(axis=0)
    importance_df = pd.DataFrame({
        'feature': feature_names,
        'importance': feature_importance
    }).sort_values('importance', ascending=False)

    print(f"✓ SHAP values computed")
    print(f"\nGround Truth Feature Importance:")
    for idx, row in importance_df.iterrows():
        print(f"  {row['feature']:12s}: {row['importance']:.4f}")

    return shap_values, base_value, importance_df


# ============================================================================
# 2. LLM LOCAL (OLLAMA)
# ============================================================================

def check_ollama():
    """Verifică dacă Ollama rulează."""
    print("\n🔌 Checking Ollama connection...")
    try:
        response = requests.get('http://localhost:11434/api/tags', timeout=2)
        if response.status_code == 200:
            models = response.json().get('models', [])
            model_names = [m['name'] for m in models]
            print(f"✓ Ollama is running")
            print(f"  Available models: {model_names}")
            return True
        else:
            print("✗ Ollama not responding correctly")
            return False
    except Exception as e:
        print(f"✗ Cannot connect to Ollama: {e}")
        print(f"  Make sure to run: ollama serve")
        return False


def generate_explanation_with_ollama(
    house_data: Dict,
    prediction: float,
    model_name: str = 'llama2:7b-chat'
) -> str:
    """Folosește Ollama (LLM local) pentru generare explicații."""

    # Format date ca string natural
    data_str = "\n".join([f"  - {k}: {v:.2f}" for k, v in house_data.items()])

    prompt = f"""You are a real estate expert. Given these house features, explain in 2-3 sentences why the model predicts this price.

House Features:
{data_str}

Predicted Price: ${prediction * 100:.1f}k

Be concise and factual. Mention which features you think matter most for this price:"""

    try:
        response = requests.post(
            'http://localhost:11434/api/generate',
            json={
                'model': model_name,
                'prompt': prompt,
                'stream': False,
                'temperature': 0.3,  # Low temp = more deterministic
            },
            timeout=30
        )

        if response.status_code == 200:
            result = response.json()
            return result.get('response', '').strip()
        else:
            return f"[Error: {response.status_code}]"
    except Exception as e:
        return f"[Error connecting to Ollama: {e}]"


# ============================================================================
# 3. EVALUATION: FAITHFULNESS
# ============================================================================

def extract_mentioned_features(explanation: str, feature_names: List[str]) -> Dict[str, float]:
    """
    Extrage din explicația în limbaj natural ce features sunt menționate.
    Returnează dict cu features și un score de "mention strength" (0-1).
    """
    mentioned = {}
    explanation_lower = explanation.lower()

    # Mapa de sinonime pentru features
    synonyms = {
        'medinc': ['income', 'median income', 'earnings', 'wealthy', 'rich'],
        'houseage': ['age', 'year built', 'old', 'new', 'vintage'],
        'averooms': ['rooms', 'bedrooms', 'large', 'spacious'],
        'avebedrms': ['bedrooms', 'bedroom', 'bed'],
        'population': ['population', 'crowded', 'dense', 'busy'],
        'aveoccup': ['occupancy', 'occupied'],
        'latitude': ['latitude', 'north', 'south', 'location', 'geographic'],
        'longitude': ['longitude', 'east', 'west', 'location', 'geographic']
    }

    for feature in feature_names:
        feature_lower = feature.lower()
        strength = 0.0

        # Direct match
        if feature_lower in explanation_lower:
            strength = 1.0
        # Synonym match
        elif feature_lower in synonyms:
            if any(syn in explanation_lower for syn in synonyms[feature_lower]):
                strength = 0.7

        if strength > 0:
            mentioned[feature] = strength

    return mentioned


def evaluate_faithfulness(
    model,
    X_test: pd.DataFrame,
    importance_df: pd.DataFrame,
    feature_names: List,
    sample_size: int = 30
) -> Dict:
    """
    Evaluează fidelitatea explicațiilor LLM vs SHAP ground truth.
    """
    print("\n🔍 Evaluating LLM faithfulness...")

    # Sample case-uri
    sample_indices = np.random.choice(len(X_test), size=min(sample_size, len(X_test)), replace=False)

    # Store rezultate
    results = {
        'cases': [],
        'top_k': 3,
        'metrics': {}
    }

    top_features = importance_df.head(3)['feature'].tolist()
    print(f"\nTop-3 important features (SHAP ground truth): {top_features}")

    mention_coverage = {f: 0 for f in top_features}
    hallucination_features = set()

    for idx, sample_idx in enumerate(sample_indices):
        print(f"\n[Case {idx+1}/{len(sample_indices)}]")

        row = X_test.iloc[sample_idx]
        house_dict = row.to_dict()
        prediction = model.predict([row.values])[0]

        # Generat explicație de la LLM
        explanation = generate_explanation_with_ollama(house_dict, prediction)
        print(f"  Prediction: ${prediction * 100:.1f}k")
        print(f"  Explanation: {explanation[:120]}...")

        # Extract mentioned features
        mentioned = extract_mentioned_features(explanation, feature_names.tolist())

        # Score fidelitate
        fidelity_score = 0.0
        case_info = {
            'index': int(sample_idx),
            'prediction': float(prediction),
            'explanation': explanation,
            'mentioned_features': mentioned,
            'top_3_covered': 0
        }

        for feat in top_features:
            if feat in mentioned:
                mention_coverage[feat] += 1
                case_info['top_3_covered'] += 1
                fidelity_score += 0.33

        # Detect hallucinations (mentioned features not in top-3)
        for feat in mentioned.keys():
            if feat not in top_features:
                hallucination_features.add(feat)

        case_info['fidelity_score'] = fidelity_score
        results['cases'].append(case_info)

        print(f"  Fidelity score: {fidelity_score:.2f}/1.0")
        print(f"  Features mentioned: {list(mentioned.keys())}")

    # Calculate metrics
    avg_fidelity = np.mean([c['fidelity_score'] for c in results['cases']])
    coverage_rate = {f: mention_coverage[f] / len(sample_indices) for f in top_features}
    hallucination_count = len(hallucination_features)

    results['metrics'] = {
        'average_fidelity': float(avg_fidelity),
        'top_3_coverage_rate': coverage_rate,
        'hallucinated_features': list(hallucination_features),
        'hallucination_count': hallucination_count,
        'sample_size': len(sample_indices)
    }

    return results


def print_evaluation_summary(results: Dict, importance_df: pd.DataFrame):
    """Print summary of faithfulness evaluation."""
    print("\n" + "="*70)
    print("📋 FAITHFULNESS EVALUATION SUMMARY")
    print("="*70)

    metrics = results['metrics']
    print(f"\n✓ Average Fidelity Score: {metrics['average_fidelity']:.2%}")
    print(f"  (How often LLM mentions top-3 most important features)")

    print(f"\nTop-3 Feature Coverage:")
    for feat, rate in metrics['top_3_coverage_rate'].items():
        bar = "█" * int(rate * 10) + "░" * (10 - int(rate * 10))
        print(f"  {feat:12s} [{bar}] {rate:.1%}")

    if metrics['hallucinated_features']:
        print(f"\n⚠️  Hallucinated Features (mentioned but not important):")
        for feat in metrics['hallucinated_features']:
            print(f"   - {feat}")
    else:
        print(f"\n✓ No hallucinated features detected!")

    print(f"\n📊 Evaluation Details:")
    print(f"  - Sample size: {metrics['sample_size']} cases")
    print(f"  - Hallucination rate: {metrics['hallucination_count']/metrics['sample_size']:.1%}")

    # Interpretare
    if metrics['average_fidelity'] > 0.7:
        print(f"\n🎯 Conclusion: LLM explanations are FAITHFUL to model predictions!")
    elif metrics['average_fidelity'] > 0.4:
        print(f"\n⚠️  Conclusion: LLM explanations are PARTIALLY faithful (some hallucinations).")
    else:
        print(f"\n❌ Conclusion: LLM explanations show HIGH hallucination rates.")

    print("="*70 + "\n")


# ============================================================================
# 4. MAIN
# ============================================================================

def main():
    print("\n" + "🚀 "*20)
    print("LLM EXPLANATION FAITHFULNESS EVALUATION")
    print("🚀 "*20 + "\n")

    # 1. Data
    df = load_and_prepare_data()

    # 2. Model
    model, X_test, y_test, feature_names = train_model(df)

    # 3. SHAP (ground truth)
    shap_values, base_value, importance_df = compute_shap_values(
        model, df.drop('Price', axis=1), X_test, feature_names
    )

    # 4. Check Ollama
    if not check_ollama():
        print("\n⚠️  Cannot proceed without Ollama.")
        print("    Install from: https://ollama.ai")
        print("    Then run: ollama pull llama2:7b-chat && ollama serve")
        return

    # 5. Evaluate faithfulness
    results = evaluate_faithfulness(model, X_test, importance_df, feature_names, sample_size=30)

    # 6. Print summary
    print_evaluation_summary(results, importance_df)

    # 7. Save results
    results_file = Path(__file__).parent / 'llm_faithfulness_results.json'
    with open(results_file, 'w') as f:
        # Convert numpy types to Python types for JSON
        def convert(obj):
            if isinstance(obj, np.integer):
                return int(obj)
            elif isinstance(obj, np.floating):
                return float(obj)
            elif isinstance(obj, np.ndarray):
                return obj.tolist()
            return obj

        json.dump(results, f, indent=2, default=convert)

    print(f"✓ Results saved to: {results_file}")

    return results


if __name__ == '__main__':
    main()
