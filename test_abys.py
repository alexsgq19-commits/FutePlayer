import urllib.request
import urllib.parse
import re
import json

def fetch(url, data=None, headers=None):
    if headers is None:
        headers = {"User-Agent": "Mozilla/5.0"}
    
    if data:
        data = urllib.parse.urlencode(data).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers=headers)
    else:
        req = urllib.request.Request(url, headers=headers)
        
    try:
        with urllib.request.urlopen(req) as response:
            return response.read().decode('utf-8')
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return ""

text1 = fetch("https://embedplayapi.top/embed/1100782")
server_match = re.search(r'class="server dropdown-item"\s+data-id="([^"]+)"', text1)
movie_match = re.search(r'data-movie-id="([^"]+)"', text1)

if server_match and movie_match:
    server_id = server_match.group(1)
    movie_id = movie_match.group(1)
    
    url2 = f"https://embedplayapi.top/ajax/get_stream_link?id={server_id}&movie={movie_id}&is_init=false"
    text2 = fetch(url2, headers={"User-Agent": "Mozilla/5.0", "X-Requested-With": "XMLHttpRequest"})
    
    try:
        data = json.loads(text2)
        link = data["data"]["link"]
        text3 = fetch(link)
        
        abys_match = re.search(r'data-id="([^"]+)">(?:.(?!data-id))*?ABYS', text3, re.DOTALL | re.IGNORECASE)
        if not abys_match:
            abys_match = re.search(r'class="player_select_item"\s+data-id="([^"]+)"', text3)
            
        if abys_match:
            player_id = abys_match.group(1)
            text4 = fetch("https://www.embedplay.one/api", data={"action": "getPlayer", "video_id": player_id})
            
            data4 = json.loads(text4)
            video_url = data4["data"]["video_url"]
            video_url = video_url.replace('\\/', '/')
            print("SUCCESS:", video_url)
    except Exception as e:
        print("ERROR:", e)

