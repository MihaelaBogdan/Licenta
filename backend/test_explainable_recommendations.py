"""
Test script for the Explainable Recommendation System.
Demonstrates all features and expected responses.
"""

import requests
import json
import os
from dotenv import load_dotenv

load_dotenv()

BASE_URL = os.getenv("FLASK_API_URL", "http://localhost:5001")

def print_section(title):
    """Print a section header."""
    print(f"\n{'='*70}")
    print(f"  {title}")
    print(f"{'='*70}\n")

def test_get_recommendations():
    """Test getting explainable recommendations."""
    print_section("TEST 1: Get Explainable Recommendations")

    payload = {
        "user_id": "mihaela-user-id",  # Replace with actual user ID
        "lat": 44.4268,  # Bucharest coordinates
        "lng": 26.1025,
        "interests": ["art", "history", "museums"],
        "language": "ro",
        "limit": 5
    }

    try:
        response = requests.post(
            f"{BASE_URL}/recommendations/explainable",
            json=payload,
            timeout=10
        )

        print(f"Status Code: {response.status_code}")
        print(f"\nRequest Payload:\n{json.dumps(payload, indent=2)}")

        if response.status_code == 200:
            data = response.json()
            print(f"\nResponse:\n{json.dumps(data, indent=2, ensure_ascii=False)}")

            # Parse and display recommendations
            if data.get("recommendations"):
                print("\n" + "─"*70)
                print("RECOMMENDATIONS BREAKDOWN:")
                print("─"*70)

                for i, rec in enumerate(data["recommendations"], 1):
                    print(f"\n🎯 RECOMMENDATION #{i}: {rec.get('name', 'Unknown')}")
                    print(f"   ⭐ Confidence: {rec.get('confidence')}")
                    print(f"   📍 Address: {rec.get('address')}")
                    print(f"   💬 Reasoning: {rec.get('reasoning')}")

                    if rec.get("explanation"):
                        exp = rec["explanation"]
                        print(f"\n   📊 Explanation Factors:")
                        for factor in exp.get("factors", []):
                            print(f"      • {factor['name']}: {factor['score']}")
                            print(f"        → {factor['description']}")

            return True
        else:
            print(f"\n❌ Error: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Request failed: {e}")
        return False

def test_get_history():
    """Test getting recommendation history."""
    print_section("TEST 2: Get Recommendation History")

    user_id = "mihaela-user-id"  # Replace with actual user ID

    try:
        response = requests.get(
            f"{BASE_URL}/recommendations/history/{user_id}?limit=10",
            timeout=10
        )

        print(f"Status Code: {response.status_code}")
        print(f"User ID: {user_id}")

        if response.status_code == 200:
            data = response.json()

            if data.get("history"):
                print(f"\n📋 Total Recommendations Tracked: {data.get('total_count')}")
                print("\nHistory:")

                for i, item in enumerate(data["history"][:5], 1):  # Show first 5
                    print(f"\n   {i}. {item.get('place_name')}")
                    print(f"      Status: {item.get('status')}")
                    print(f"      Confidence: {item.get('confidence')}%")
                    print(f"      Recommended: {item.get('recommended_at')}")

                return True
            else:
                print("\n⚠️ No recommendation history found")
                return False
        else:
            print(f"\n❌ Error: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Request failed: {e}")
        return False

def test_get_stats():
    """Test getting recommendation statistics."""
    print_section("TEST 3: Get Recommendation Statistics")

    user_id = "mihaela-user-id"  # Replace with actual user ID

    try:
        response = requests.get(
            f"{BASE_URL}/recommendations/stats/{user_id}",
            timeout=10
        )

        print(f"Status Code: {response.status_code}")
        print(f"User ID: {user_id}")

        if response.status_code == 200:
            data = response.json()

            if data.get("status") == "success":
                print("\n📊 RECOMMENDATION STATISTICS:")
                print(f"   Total Recommendations: {data.get('total_recommendations')}")
                print(f"   Visited: {data.get('visited_count')}")
                print(f"   Accepted: {data.get('accepted_count')}")
                print(f"   Rejected: {data.get('rejected_count')}")
                print(f"   Accuracy Rate: {data.get('accuracy_rate')}")
                print(f"   Average Confidence: {data.get('avg_confidence')}")

                return True
            else:
                print(f"\n⚠️ {data.get('error', 'Unknown error')}")
                return False
        else:
            print(f"\n❌ Error: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Request failed: {e}")
        return False

def test_update_status():
    """Test updating recommendation status."""
    print_section("TEST 4: Update Recommendation Status")

    # First, get a recommendation ID from history
    print("📌 Note: This test requires an actual recommendation ID from the database")
    print("   In a real scenario, you would get this from the history endpoint\n")

    payload = {
        "status": "visited"  # or 'accepted', 'rejected', 'pending'
    }

    # Example with mock recommendation ID
    rec_id = "550e8400-e29b-41d4-a716-446655440000"  # Replace with real ID

    try:
        response = requests.patch(
            f"{BASE_URL}/recommendations/{rec_id}/status",
            json=payload,
            timeout=10
        )

        print(f"Status Code: {response.status_code}")
        print(f"Recommendation ID: {rec_id}")
        print(f"New Status: {payload['status']}")

        if response.status_code == 200:
            data = response.json()
            print(f"\n✅ Success: {data.get('message', 'Status updated')}")
            return True
        else:
            print(f"\n⚠️ Response: {response.text}")
            # This might fail with mock ID, which is expected
            return False

    except Exception as e:
        print(f"❌ Request failed: {e}")
        return False

def test_compare_users():
    """Test comparing recommendations between users."""
    print_section("TEST 5: Compare User Recommendations")

    user_id_1 = "mihaela-user-id"  # Replace
    user_id_2 = "other-user-id"    # Replace

    try:
        response = requests.get(
            f"{BASE_URL}/recommendations/comparison?user_id_1={user_id_1}&user_id_2={user_id_2}",
            timeout=10
        )

        print(f"Status Code: {response.status_code}")
        print(f"Comparing users: {user_id_1} vs {user_id_2}")

        if response.status_code == 200:
            data = response.json()

            print("\n👤 User 1:")
            print(f"   ID: {data['user_1']['user_id']}")
            print(f"   Total Recommendations: {data['user_1']['total_recommendations']}")

            print("\n👤 User 2:")
            print(f"   ID: {data['user_2']['user_id']}")
            print(f"   Total Recommendations: {data['user_2']['total_recommendations']}")

            print(f"\n🔄 Similarity: {data.get('similarity_percentage')}")
            print(f"   Common Places: {len(data.get('common_recommendations', []))}")

            if data.get("common_recommendations"):
                print("\n   Shared Recommendations:")
                for place in data["common_recommendations"][:5]:
                    print(f"      • {place}")

            return True
        else:
            print(f"\n⚠️ Response: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Request failed: {e}")
        return False

def run_all_tests():
    """Run all tests."""
    print("\n" + "="*70)
    print("  EXPLAINABLE RECOMMENDATION SYSTEM - TEST SUITE")
    print("="*70)

    results = []

    results.append(("Get Recommendations", test_get_recommendations()))
    results.append(("Get History", test_get_history()))
    results.append(("Get Statistics", test_get_stats()))
    results.append(("Update Status", test_update_status()))
    results.append(("Compare Users", test_compare_users()))

    # Summary
    print_section("TEST SUMMARY")

    for test_name, passed in results:
        status = "✅ PASSED" if passed else "❌ FAILED"
        print(f"  {test_name:<40} {status}")

    print("\n" + "="*70 + "\n")

if __name__ == "__main__":
    # Make sure to update user IDs and coordinates for your test data
    run_all_tests()
