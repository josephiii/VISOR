
class VLModel:

    def __init__(self):
        self.model = None
        self.processor = None

        self.MODEL_ID : str
        self.MODEL_REVISION : str

        #self.device_map : str = "cpu" #"auto"# didnt wokr

    def load_model(self):
        pass
    def unload_model(self):
        pass
    def describe_image(self, image_path : str, prompt : str) -> str:
        pass
