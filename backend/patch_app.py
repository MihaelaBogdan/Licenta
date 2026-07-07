import os

with open('app.py', 'r') as f:
    content = f.read()

# 1. Remove dummy_follows and dummy_users logic
content = content.replace("dummy_follows = set()\n", "")

search_route = """@app.get("/users/search")
def search_users():
    query = request.args.get("query", "")
    current_user_id = request.args.get("current_user_id", "")
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?or=(name.ilike.*{query}*,email.ilike.*{query}*,username.ilike.*{query}*)&limit=20"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
        
        users = res.json()

        for user in users:
            u_id = user.get("id")
            if u_id == current_user_id:
                user["is_me"] = True
                continue
            
            # Check pending
            pending = [p for p in pending_follows_db if p["follower_id"] == current_user_id and p["following_id"] == u_id]
            if pending:
                user["is_following"] = False
                user["is_requested"] = True
            else:
                check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{current_user_id}&following_id=eq.{u_id}&select=id"
                follow_res = requests.get(check_url, headers=headers).json()
                user["is_following"] = len(follow_res) > 0
                user["is_requested"] = False
            
        return jsonify(users)
    except Exception as e:
        print(f"❌ User Search Error: {e}")
        return jsonify([])"""

# Replace search route
start_search = content.find("@app.get(\"/users/search\")")
end_search = content.find("@app.post(\"/users/<user_id>/preferred-location\")")

if start_search != -1 and end_search != -1:
    content = content[:start_search] + search_route + "\n\n" + content[end_search:]

new_follow_routes = """
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

    # Check if already follows in DB
    check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}&select=id"
    existing = requests.get(check_url, headers=headers).json()

    if existing:
        del_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}"
        requests.delete(del_url, headers=headers)
        return jsonify({"status": "unfollowed"})

    # Check pending
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
        
    requests_for_me = [p for p in pending_follows_db if p["following_id"] == user_id]
    
    if not requests_for_me:
        return jsonify([])
        
    follower_ids = [p["follower_id"] for p in requests_for_me]
    in_clause = ",".join([f'"{id}"' for id in follower_ids])
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=in.({in_clause})&select=id,name,username,avatar"
    res = requests.get(url, headers=headers)
    
    if res.status_code != 200:
        return jsonify([])
        
    profiles = res.json()
    
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
    action = data.get("action")
    
    global pending_follows_db
    req = next((p for p in pending_follows_db if p["id"] == req_id), None)
    
    if not req:
        return jsonify({"error": "Request not found"}), 404
        
    pending_follows_db = [p for p in pending_follows_db if p["id"] != req_id]
    
    if action == "accept":
        headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json",
            "Prefer": "return=minimal"
        }
        ins_data = {"follower_id": req["follower_id"], "following_id": req["following_id"]}
        url = f"{SUPABASE_URL}/rest/v1/user_follows"
        requests.post(url, headers=headers, json=ins_data)
        return jsonify({"status": "accepted"})
    
    return jsonify({"status": "rejected"})
"""

start_follow = content.find("@app.post(\"/users/follow\")")
end_follow = content.find("@app.get(\"/user/activity\")")

if start_follow != -1 and end_follow != -1:
    content = content[:start_follow] + new_follow_routes + "\n\n" + content[end_follow:]

with open('app.py', 'w') as f:
    f.write(content)

print("Backend App Patched!")
