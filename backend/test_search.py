import requests
query = "Miha"
current_user_id = "8e875b0f-87f3-4e1a-bd08-1c506716f9cf"
url = f"http://127.0.0.1:5002/users/search?query={query}&current_user_id={current_user_id}"
res = requests.get(url)
print(res.text)
