
from fastapi import FastAPI
from routes.AuthRoutes import auth_router
from routes.VLMRoutes import vlm_router

app = FastAPI(title="VISOR")

app.include_router(auth_router)
app.include_router(vlm_router)