# import tensorflow as tf
# from tflite_model_maker import image_classifier
# from tflite_model_maker.image_classifier import DataLoader
#
# # data/keys/*.jpg, data/wallet/*.jpg, data/cane/*.jpg, data/background/*.jpg
# data = DataLoader.from_folder('data/')
# train_data, test_data = data.split(0.9)
#
# model = image_classifier.create(
#     train_data,
#     model_spec='mobilenet_v2',   # or 'efficientnet_lite0' for better accuracy/size tradeoff
#     epochs=15,
#     batch_size=32,
# )
#
# loss, accuracy = model.evaluate(test_data)
# print(f"Test accuracy: {accuracy:.2%}")
#
# model.export(export_dir='export/', label_filename='labels.txt')
# # produces export/model.tflite + export/labels.txt with metadata baked in