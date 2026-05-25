from typing import Any, Optional
"""Apple App Store Connect API client."""

import time
from typing import Any
import httpx
import jwt


class AppStoreConnectClient:
    BASE_URL = "https://api.appstoreconnect.apple.com/v1"

    def __init__(self, key_id: str, issuer_id: str, private_key: str, app_id: Optional[str] = None):
        self.key_id = key_id
        self.issuer_id = issuer_id
        self.private_key = private_key.replace("\\n", "\n")
        self.app_id = app_id
        self._token = None
        self._token_expires = 0

    def _generate_token(self) -> str:
        now = time.time()
        if self._token and now < self._token_expires - 60:
            return self._token
        payload = {"iss": self.issuer_id, "iat": int(now), "exp": int(now + 1200), "aud": "appstoreconnect-v1"}
        self._token = jwt.encode(payload, self.private_key, algorithm="ES256", headers={"kid": self.key_id})
        self._token_expires = now + 1200
        return self._token

    def _request(self, method: str, endpoint: str, **kwargs) -> dict[str, Any]:
        token = self._generate_token()
        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        url = f"{self.BASE_URL}/{endpoint}" if not endpoint.startswith("http") else endpoint
        with httpx.Client(timeout=30.0) as client:
            response = client.request(method, url, headers=headers, **kwargs)
            response.raise_for_status()
            return response.json() if response.text else {}

    def list_apps(self) -> list[dict[str, Any]]:
        return self._request("GET", "apps").get("data", [])

    def get_app_store_versions(self, app_id: Optional[str] = None) -> list[dict[str, Any]]:
        aid = app_id or self.app_id
        if not aid:
            raise ValueError("app_id required")
        return self._request("GET", f"apps/{aid}/appStoreVersions").get("data", [])

    def get_customer_reviews(self, app_id: Optional[str] = None, limit: int = 50) -> list[dict[str, Any]]:
        aid = app_id or self.app_id
        if not aid:
            raise ValueError("app_id required")
        return self._request("GET", f"apps/{aid}/customerReviews", params={"limit": limit}).get("data", [])
