#!/usr/bin/env python3
import os
from dotenv import load_dotenv
load_dotenv()

print("Testing Apple App Store Connect...")
try:
    from appstore_mcp.app_store_connect import AppStoreConnectClient
    client = AppStoreConnectClient(os.environ["APPLE_KEY_ID"], os.environ["APPLE_ISSUER_ID"], os.environ["APPLE_PRIVATE_KEY"])
    apps = client.list_apps()
    print(f"SUCCESS! Found {len(apps)} app(s)")
    for app in apps:
        print(f"  - {app.get('attributes', {}).get('name')}")
except Exception as e:
    print(f"Error: {e}")

print("\nTesting Google Play Console...")
try:
    from appstore_mcp.google_play import GooglePlayClient
    client = GooglePlayClient(os.environ["GOOGLE_SERVICE_ACCOUNT_JSON"], os.environ["GOOGLE_PACKAGE_NAME"])
    tracks = client.get_app_version_info()
    print(f"SUCCESS! Found {len(tracks)} track(s)")
except Exception as e:
    print(f"Error: {e}")
