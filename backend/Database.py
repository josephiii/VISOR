
import os
from supabase import create_client, Client
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_SECRET_KEY = os.environ["SUPABASE_SECRET_KEY"]


def supabase_client() -> Client:
    return create_client(SUPABASE_URL, SUPABASE_SECRET_KEY)