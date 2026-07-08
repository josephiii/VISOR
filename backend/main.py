
from fastapi import FastAPI
from routes.AuthRoutes import router

app = FastAPI(title="VISOR")

app.include_router(router, prefix="/auth")