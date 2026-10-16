# Overview

For the purposes of this project, the object detection model and the depth estimation model must work with each other. We need to be able to detect hazards at certain distances and alert the user of what hazard the system notices and how far away it is. The depth estimation model will handle the how far away it is part, but we need to attach the image to a fairly lightweight model keen for spotting out hazards. Together these models intend to provide a more effective product for the user.

# Considerations

For this project, real-time processing is a must. Therefore, any model chosen must meet a certain speed to even be considered, leaving one name above the rest : YOLO. Models such as YOLO11, YOLO26, and YOLOv8 are all to be considered here.

Of these 3 YOLO11 is the most reliable. YOLO8 is the oldest of the three models and has all the same benefits as YOLO11, but with YOLO11 offer more. YOLO26 is newer, slightly faster, and architecturally better with its NMS-free design but less battle tested. YOLO11 has real world use and troubleshooting that YOLO26 may currently not have.

## How YOLO Works

YOLO takes the input from an image and converts it into a grid. All at the same time, each grid cell determines if the center of an object is in a cell, where the bounding box of the object is ("objectness"), and what class of item it is. The convolutional network is what makes it fast enough for real time use. The backbone turns a raw image into layered feature maps that are later fused so the object can still pick up on objects at different scale in the image. The result is the grid predictions with boxes, "objectness", and class probabilities. Those classes are what we can modify to include the things that are deemed hazards that our audience should be alerted about.

### Fine tuning

As previously stated the model takes raw images and turns them into layered feature maps. The backbone, layers 0-10, need to be built upon to fine tune the model. When training the model, by setting the freeze parameter equal to 11, the entire backbone is frozen. Since tine-tuning converges much faster than training, start epochs off at a moderate value relative to the amount of data. The patience hyperparameter can be used to stop the model early once the validation metrics start to plateau.

##### 2 Stage Fine Tuning

In 2-stage fine tuning, the neck and head of the model are trained to adapt to the new classes. Afterwards, the backbone is unfrozen and trained at a lower learning rate to refine the backbone for the target domain.

##### Forgetting/ No Predictions

After fine-tuning, the model will have performance downgrades for its original classes. Forgetting is almost unavoidable without using images from the original training set along with the set for fine-tuning. Merging datasets, freezing the backbone and neck, and training for fewer epochs can help mitigate this.

If the model fails to produce predictions, there was likely insufficient training data provided. Enough diverse examples must be provided so that model can generalize. In addition, lowering the confidence threshold can fix the no prediction issue if predictions do exist but are getting filtered out.

Any training or fine tuning requires a YAML file to introduce the new classes of items.

```
# Dataset root (absolute or relative to where you run training)
path: /home/me/datasets/my_dataset

# Image folders (relative to path)
train: images/train
val: images/val
# test: images/test    # optional

# Classes (index → name)
names:
  0: forklift
  1: person
  2: pallet
```

YOLO expects a specific structure with exact folder names within the hazards folders. The stem of corresponding files must match.

```
hazards/
  images/
    train/
      img001.jpg
      img002.jpg
    val/
      img050.jpg
  labels/
    train/
      img001.txt
      img002.txt
    val/
      img050.txt
```

In each .txt file, there needs to be numbers detailing the class id of the pictured item, the horizontal center coordinate, the vertical center coordinate, the width, and the height.

```
class_id  x_center  y_center  width  height
2  0.45  0.72  0.08  0.06
```

#### Simplified Pipeline

1. Take lots of pictures of each item (various lighting, angles, sizes, other variations)
2. Organize the folder structure
3. Take each image and in some software like RoboFlow/CVAT draw boxes aorund images
4. Export .txt files in the above format to the folder
5. Write the yaml file with the classes
6. Train the model in Python
