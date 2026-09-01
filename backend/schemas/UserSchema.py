
from pydantic import BaseModel
from enum import Enum

class VisionType(str, Enum):
    CENTRAL_LOSS = "central_loss"
    PERIPHERAL_LOSS = "peripheral_loss" 
    BLUR_LOW_ACUITY = "blur_low_acuity" 
    CONTRAST_LIGHT = "contrast_light"
    NOT_SURE = "not_sure"

class Severity(str, Enum):
    MILD = "mild"
    MODERATE = "moderate"
    SEVERE = "severe"

class SpeechRate(str, Enum):
    SLOW = "slow" 
    NORMAL = "normal"
    FAST = "fast"

class Verbosity(str, Enum):
    BRIEF = "brief" 
    STANDARD = "standard" 
    DETAILED = "detailed"

class SettingsSchema(BaseModel):
    username: str
    visionType: list[VisionType]
    visionDescription: str
    severity: Severity
    speechRate: SpeechRate
    verbosity: Verbosity
    appHighContrast: bool
