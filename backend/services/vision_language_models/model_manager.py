from model_qwen2_vl import Qwen2_VL

def main():
    model = Qwen2_VL()
    model.load_model()
    text : str = model.describe_image("images.jpg", "Describe this image")
    print(text)
    model.unload_model()

if __name__ == "__main__":
    main()