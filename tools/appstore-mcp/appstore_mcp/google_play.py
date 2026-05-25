"""Google Play Console API client."""

import json
from datetime import datetime, timedelta
from typing import Any

from google.oauth2 import service_account
from googleapiclient.discovery import build


class GooglePlayClient:
    """Client for Google Play Console API."""

    SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

    def __init__(self, service_account_json: str, package_name: str):
        self.package_name = package_name
        creds_dict = json.loads(service_account_json)
        self.credentials = service_account.Credentials.from_service_account_info(
            creds_dict, scopes=self.SCOPES
        )
        self.service = build("androidpublisher", "v3", credentials=self.credentials)

    def get_app_details(self) -> dict[str, Any]:
        result = self.service.edits().insert(body={}, packageName=self.package_name).execute()
        edit_id = result["id"]
        details = self.service.edits().details().get(packageName=self.package_name, editId=edit_id).execute()
        listings = self.service.edits().listings().list(packageName=self.package_name, editId=edit_id).execute()
        self.service.edits().delete(packageName=self.package_name, editId=edit_id).execute()
        return {"details": details, "listings": listings.get("listings", [])}

    def get_reviews(self, max_results: int = 50) -> list[dict[str, Any]]:
        result = self.service.reviews().list(packageName=self.package_name, maxResults=max_results).execute()
        return result.get("reviews", [])

    def get_app_version_info(self) -> dict[str, Any]:
        result = self.service.edits().insert(body={}, packageName=self.package_name).execute()
        edit_id = result["id"]
        try:
            tracks = self.service.edits().tracks().list(packageName=self.package_name, editId=edit_id).execute()
        finally:
            self.service.edits().delete(packageName=self.package_name, editId=edit_id).execute()
        return tracks.get("tracks", [])
