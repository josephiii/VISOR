from pydantic import BaseModel

class ImagePromptSchema(BaseModel):
    prompt: str

