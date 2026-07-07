import re

with open('app.py', 'r') as f:
    content = f.read()

new_routes = """
pending_follows_db = []

@app.post("/users/follow")
def toggle_follow():
    data = request.get_json()
    f_id = data.get("follower_id")
    t_id = data.get("following_id")

    if not f_id or not t_id:
        return jsonify({"error": "Missing IDs"}), 400

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }

    if str(t_id).startswith("dummy-"):
        key = f"{f_id}_{t_id}"
        if key in dummy_follows:
            dummy_follows.remove(key)
            return jsonify({"status": "unfollowed"})
        else:
            dummy_follows.add(key)
            return jsonify({"status": "requested"}) # Changed to requested for demo

    # Check if exists in DB (already accepted)
    check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}&select=id"
    existing = requests.get(check_url, headers=headers).json()

    if existing:
        # Unfollow (already accepted)
        del_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}"
        requests.delete(del_url, headers=headers)
        return jsonify({"status": "unfollowed"})
    
    # Check if pending request exists
    global pending_follows_db
    pending = [p for p in pending_follows_db if p["follower_id"] == f_id and p["following_id"] == t_id]
    if pending:
        # Cancel request
        pending_follows_db = [p for p in pending_follows_db if not (p["follower_id"] == f_id and p["following_id"] == t_id)]
        return jsonify({"status": "unfollowed"})
    else:
        import uuid
        req_id = str(uuid.uuid4())
        pending_follows_db.append({
            "id": req_id,
            "follower_id": f_id,
            "following_id": t_id,
            "status": "pending"
        })
        return jsonify({"status": "requested"})

@app.get("/users/follow/requests")
def get_follow_requests():
    user_id = request.args.get("user_id")
    if not user_id:
        return jsonify([]), 400
        
    # Get all pending for this user
    requests_for_me = [p for p in pending_follows_db if p["following_id"] == user_id]
    
    if not requests_for_me:
        return jsonify([])
        
    # We need to fetch the profiles of the followers to show their names and avatars
    follower_ids = [p["follower_id"] for p in requests_for_me]
    in_clause = ",".join([f"\"{id}\"" for id in follower_ids])
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=in.({in_clause})&select=id,name,username,avatar"
    res = requests.get(url, headers=headers)
    
    if res.status_code != 200:
        return jsonify([])
        
    profiles = res.json()
    
    # Merge
    result = []
    for req in requests_for_me:
        profile = next((p for p in profiles if p["id"] == req["follower_id"]), None)
        if profile:
            result.append({
                "request_id": req["id"],
                "follower_id": profile["id"],
                "name": profile.get("name"),
                "username": profile.get("username"),
                "avatar": profile.get("avatar"),
                "status": req["status"]
            })
            
    return jsonify(result)

@app.post("/users/follow/respond")
def respond_follow_request():
    data = request.get_json()
    req_id = data.get("request_id")
    action = data.get("action") # 'accept' or 'reject'
    
    global pending_follows_db
    req = next((p for p in pending_follows_db if p["id"] == req_id), None)
    
    if not req:
        return jsonify({"error": "Request not found"}), 404
        
    pending_follows_db = [p for p in pending_follows_db if p["id"] != req_id]
    
    if action == "accept":
        # Insert into DB
        headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json"
        }
        ins_data = {"follower_id": req["follower_id"], "following_id": req["following_id"]}
        url = f"{SUPABASE_URL}/rest/v1/user_follows"
        requests.post(url, headers=headers, json=ins_data)
        return jsonify({"status": "accepted"})
    
    return jsonify({"status": "rejected"})
"""

# Replace the toggle_follow block
start_idx = content.find("@app.post(\"/users/follow\")")
if start_idx != -1:
    end_idx = content.find("@app.get(\"/user/activity\")", start_idx)
    if end_idx == -1:
        # fallback if not found
        end_idx = content.find("@app.post", start_idx + 30)
    
    if end_idx != -1:
        new_content = content[:start_idx] + new_routes + "\n\n" + content[end_idx:]
        with open('app.py', 'w') as f:
            f.write(new_content)
        print("Success")
    else:
        print("End index not found")
else:
    print("Start index not found")
