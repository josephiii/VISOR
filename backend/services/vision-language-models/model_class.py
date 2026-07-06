
class VLModel:

    def __init__(self):
        self.model = None
        self.processor = None
        self.MODEL_ID : str

    def load_model(self):
        pass
    def unload_model(self):
        pass
    def describe_image(self, image_path : str, prompt : str) -> str:
        pass
