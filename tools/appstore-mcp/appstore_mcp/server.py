"""MCP Server for App Store Analytics."""
import json
import os
from datetime import datetime
from typing import Any
from dotenv import load_dotenv
from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import TextContent, Tool
from .app_store_connect import AppStoreConnectClient
from .google_play import GooglePlayClient

load_dotenv()
server = Server("appstore-mcp")

def get_google_client():
    return GooglePlayClient(os.environ.get("GOOGLE_SERVICE_ACCOUNT_JSON", ""), os.environ.get("GOOGLE_PACKAGE_NAME", ""))

def get_apple_client():
    return AppStoreConnectClient(os.environ.get("APPLE_KEY_ID", ""), os.environ.get("APPLE_ISSUER_ID", ""), os.environ.get("APPLE_PRIVATE_KEY", ""))

@server.list_tools()
async def list_tools() -> list[Tool]:
    return [Tool(name="morning_report", description="Generate daily app store report", inputSchema={"type": "object", "properties": {}})]

@server.call_tool()
async def call_tool(name: str, arguments: dict[str, Any]) -> list[TextContent]:
    if name == "morning_report":
        lines = ["=" * 50, f"SPEARO APP STORE REPORT - {datetime.now().strftime('%B %d, %Y')}", "=" * 50]
        try:
            apple = get_apple_client()
            apps = apple.list_apps()
            for app in apps[:3]:
                attrs = app.get("attributes", {})
                lines.append(f"Apple: {attrs.get('name')} - {attrs.get('bundleId')}")
        except Exception as e:
            lines.append(f"Apple error: {e}")
        try:
            google = get_google_client()
            tracks = google.get_app_version_info()
            for t in tracks:
                if t.get("releases"):
                    lines.append(f"Google {t['track']}: {t['releases'][0].get('status')}")
        except Exception as e:
            lines.append(f"Google error: {e}")
        return [TextContent(type="text", text="\n".join(lines))]
    return [TextContent(type="text", text="Unknown tool")]

async def run():
    async with stdio_server() as (r, w):
        await server.run(r, w, server.create_initialization_options())

def main():
    import asyncio
    asyncio.run(run())

if __name__ == "__main__":
    main()
