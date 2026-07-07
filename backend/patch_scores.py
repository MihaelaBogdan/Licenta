with open("explainable_recommender.py", "r") as f:
    content = f.read()

# Make the total score precise
import re
new_calc = """        total_score = (interest_score * 0.45) + (history_score * 0.20) + (bookmarks_score * 0.15) + (popularity_score * 0.10) + (weather_time_score * 0.10)
        
        # Add precision
        total_score = round(total_score, 2)
        interest_score = round(interest_score * 0.45, 2)
        history_score = round(history_score * 0.20, 2)
        bookmarks_score = round(bookmarks_score * 0.15, 2)
        popularity_score = round(popularity_score * 0.10, 2)
        weather_time_score = round(weather_time_score * 0.10, 2)
        
        scores["interest_match"] = interest_score
        scores["history_affinity"] = history_score
        scores["bookmarks"] = bookmarks_score
        scores["popularity"] = popularity_score
        scores["weather_time"] = weather_time_score
        scores["total"] = total_score"""

# We just find where total is calculated and replace it.
start = content.find("total_score = (interest_score * 0.35) +")
if start != -1:
    end = content.find("return scores", start)
    content = content[:start] + new_calc + "\n\n        " + content[end:]
    with open("explainable_recommender.py", "w") as f:
        f.write(content)
    print("Scores Patched!")
else:
    print("Could not find total_score calc.")
