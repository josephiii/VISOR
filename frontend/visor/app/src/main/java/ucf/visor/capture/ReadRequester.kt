
package ucf.visor.capture

// interface that joins the voice to reading gap
interface ReadRequester {
    fun requestRead(onText: (String) -> Unit)
}