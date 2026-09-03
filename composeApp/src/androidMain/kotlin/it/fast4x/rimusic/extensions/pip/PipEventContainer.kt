package it.fast4x.rimusic.extensions.pip

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.fast4x.rimusic.utils.PinchDirection
import it.fast4x.rimusic.utils.pinchToToggle

@Composable
fun PipEventContainer(
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    autoEnterIfPossible: Boolean = true,
    onPipOutAction: () -> Unit = {},
    content: @Composable () -> Unit
){
    val pipHandler = rememberPipHandler()

    Pip(
        numerator = 1,
        denominator = 1,
        modifier = modifier,
        autoEnterIfPossible = autoEnterIfPossible
    ) {
        Box(
            modifier = if (enable)
                modifier
                    .pinchToToggle(
                        direction = PinchDirection.Out,
                        threshold = 1.05f,
                        onPinch = { onPipOutAction() }
                    )
                    .pinchToToggle(
                        direction = PinchDirection.In,
                        threshold = .95f,
                        onPinch = { pipHandler.enterPictureInPictureMode() }
                    )
            else
                modifier

        ) {
            content()
        }
    }
}
