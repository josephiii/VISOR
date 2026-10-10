// MockDeviceInfo encapsulates:
// - device: the MockGlasses instance returned by MockDeviceKit.pairGlasses(model)
// - model: which glasses it simulates (META_RAYBAN_DISPLAY adds the 1.0 mock display)
// - deviceId: the SDK's DeviceIdentifier for the simulated pair
// - deviceName: Display name for the simulated device
// - hasCameraFeed: Whether mock video content has been configured
// - hasCapturedImage: Whether mock photo content has been configured
// - cameraSource: Which phone camera is being used as the source, if any
// - hasMotionFeed: Whether a synthetic IMU recording is replaying (MWDAT 1.0 MockMotionKit)

package ucf.visor.ui.viewmodel_d


import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing

data class MockDeviceInfo(
    val device: MockGlasses,
    val model: GlassesModel,
    val deviceId: String,
    val deviceName: String,
    val hasCameraFeed: Boolean = false,
    val hasCapturedImage: Boolean = false,
    val cameraSource: CameraFacing? = null,
    val hasMotionFeed: Boolean = false,
    val isPoweredOn: Boolean = false,
    val isDonned: Boolean = false,
    val isUnfolded: Boolean = false,
)

data class DebugUiState(
    val isEnabled: Boolean = false,
    val pairedDevices: List<MockDeviceInfo> = emptyList(),
    /** The last thing a debug action reported, e.g. a simulated voice launch's request id. */
    val lastAction: String? = null,
)
