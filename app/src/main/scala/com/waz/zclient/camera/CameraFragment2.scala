package com.waz.zclient.camera

import java.util
import java.util.Set

import android.animation.{Animator, AnimatorListenerAdapter, ObjectAnimator}
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.support.annotation.Nullable
import android.view.{LayoutInflater, View, ViewGroup}
import android.view.animation.Animation
import android.widget.{FrameLayout, TextView}
import com.waz.api.ImageAsset
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURI, AndroidURIUtil, URI}
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.camera.views.CameraPreviewTextureView
import com.waz.zclient.controllers.accentcolor.AccentColorObserver
import com.waz.zclient.controllers.camera.CameraActionObserver
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.orientation.OrientationControllerObserver
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.{ImagePreviewCallback, ImagePreviewLayout}
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.pages.main.profile.camera.{CameraContext, CameraFocusView, ProfileToCameraAnimation}
import com.waz.zclient.pages.main.profile.camera.controls.{CameraBottomControl, CameraTopControl}
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.utils.{RichView, SquareOrientation, ViewUtils}
import com.waz.zclient.views.ProgressView

class CameraFragment2 extends BaseFragment[CameraFragment2.Container]
  with FragmentHelper
  with CameraPreviewObserver
  with OrientationControllerObserver
  with AccentColorObserver
  with ImagePreviewCallback
  with CameraTopControl.CameraTopControlCallback
  with CameraBottomControl.CameraBottomControlCallback {

  private lazy val cameraContext = CameraContext.getFromOrdinal(getArguments.getInt(CameraFragment2.CAMERA_CONTEXT))

  private lazy val imagePreviewContainer = view[FrameLayout](R.id.fl__preview_container)
  private lazy val previewProgressBar = view[ProgressView](R.id.pv__preview)

  //TODO allow selection of a camera 'facing' for different cameraContexts
  private var cameraPreview = returning(view[CameraPreviewTextureView](R.id.cptv__camera_preview)) {
    _.foreach(_.setObserver(this))
  }

  private lazy val cameraNotAvailableTextView = view[TextView](R.id.ttv__camera_not_available_message)

  private lazy val cameraTopControl = returning(view[CameraTopControl](R.id.ctp_top_controls)) { _.foreach { view =>
    view.setCameraTopControlCallback(this)
    view.setAlpha(0)
    view.setVisible(true)
  }}

  private lazy val cameraBottomControl = returning(view[CameraBottomControl](R.id.cbc__bottom_controls)) { _.foreach { view =>
    view.setCameraBottomControlCallback(this)
    view.setMode(cameraContext)
    view.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {} // do nothing but consume event
    })
  }}

  private lazy val focusView = view[CameraFocusView](R.id.cfv__focus)

  private lazy val intentsManager: AssetIntentsManager = new AssetIntentsManager(getActivity, new AssetIntentsManager.Callback() {
    override def onDataReceived(t: AssetIntentsManager.IntentType, uri: URI): Unit = processGalleryImage(uri)
    override def onCanceled(t: AssetIntentsManager.IntentType): Unit = showCameraFeed()
    override def onFailed(t: AssetIntentsManager.IntentType): Unit = showCameraFeed()
    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = startActivityForResult(intent, intentType.requestCode)
  }, new AndroidURI(getArguments.getParcelable[Uri](AssetIntentsManager.SAVED_STATE_PENDING_URI)))

  private var alreadyOpenedGallery: Boolean = false
  private var cameraPreviewAnimationDuration: Int = 0
  private var cameraControlAnimationDuration: Int = 0


  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = { // opening from profile
    if (nextAnim == R.anim.camera__from__profile__transition) {
      val controlHeight: Int = getResources.getDimensionPixelSize(R.dimen.camera__control__height)
      return new ProfileToCameraAnimation(enter, getResources.getInteger(R.integer.framework_animation_duration_medium), 0, controlHeight, 0)
    }
    super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreateView(inflater: LayoutInflater, c: ViewGroup, savedInstanceState: Bundle): View = {
    val view: View = inflater.inflate(R.layout.fragment_camera, c, false)

    cameraTopControl
    cameraBottomControl

    imagePreviewContainer
    previewProgressBar

    intentsManager

    if (savedInstanceState != null) alreadyOpenedGallery = savedInstanceState.getBoolean(CameraFragment2.ALREADY_OPENED_GALLERY)
    cameraControlAnimationDuration = getResources.getInteger(R.integer.camera__control__ainmation__duration)
    cameraPreviewAnimationDuration = getResources.getInteger(R.integer.camera__preview__ainmation__duration)
    view.setBackgroundResource(R.color.black)
    view
  }

  override def onStart(): Unit = {
    super.onStart()
    getControllerFactory.getAccentColorController.addAccentColorObserver(this)
    getControllerFactory.getOrientationController.addOrientationControllerObserver(this)
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    outState.putBoolean(CameraFragment2.ALREADY_OPENED_GALLERY, alreadyOpenedGallery)
    intentsManager.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override def onStop(): Unit = {
    getControllerFactory.getAccentColorController.removeAccentColorObserver(this)
    getControllerFactory.getOrientationController.removeOrientationControllerObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    hideCameraFeed()
    cameraBottomControl.foreach(
      _.animate
        .translationY(getView.getMeasuredHeight)
        .setDuration(cameraControlAnimationDuration)
        .setInterpolator(new Expo.EaseIn)
    )
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = {
    onClose()
    true
  }

  private def disableCameraButtons(): Unit = if (Option(getView).isDefined) {
    findById(R.id.gtv__camera_control__take_a_picture).setVisibility(View.GONE)
    findById(R.id.gtv__camera__top_control__change_camera).setVisibility(View.GONE)
    findById(R.id.gtv__camera__top_control__flash_setting).setVisibility(View.GONE)
  }

  override def onCameraLoaded(flashModes: util.Set[FlashMode]): Unit = {
    cameraPreview.foreach { preview =>
      cameraTopControl.foreach { control =>
        control.setFlashStates(flashModes, preview.getCurrentFlashMode)
        control.enableCameraSwitchButtion(preview.getNumberOfCameras > 1)
      }
    }

    showCameraFeed()
    val openGalleryArg: Boolean = getArguments.getBoolean(CameraFragment2.SHOW_GALLERY)
    if (!alreadyOpenedGallery && openGalleryArg) {
      alreadyOpenedGallery = true
      openGallery()
    }
    cameraNotAvailableTextView.foreach(_.setVisible(false))
  }

  override def onCameraLoadingFailed(): Unit = {
    if (getContainer != null) getControllerFactory.getCameraController.onCameraNotAvailable(cameraContext)
    disableCameraButtons()
    cameraNotAvailableTextView.foreach(_.setVisible(true))
  }

  override def onCameraReleased(): Unit = {
    //no need to override since we don't exit the app
  }

  override def onPictureTaken(imageData: Array[Byte], isMirrored: Boolean): Unit =
   showPreview { _.setImage(imageData, isMirrored) }

  override def onFocusBegin(focusArea: Rect): Unit = focusView.foreach { view =>
    view.setColor(getControllerFactory.getAccentColorController.getColor)
    view.setX(focusArea.centerX - view.getWidth / 2)
    view.setY(focusArea.centerY - view.getHeight / 2)
    view.showFocusView()
  }

  override def onFocusComplete(): Unit = focusView.foreach {
    _.hideFocusView()
  }

  def openGallery(): Unit = {
    intentsManager.openGallery()
    getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
  }

  override def nextCamera(): Unit = cameraPreview.foreach(_.nextCamera())

  override def setFlashMode(mode: FlashMode): Unit = cameraPreview.foreach(_.setFlashMode(mode))

  override def getFlashMode: FlashMode = cameraPreview.map(_.getCurrentFlashMode).getOrElse(FlashMode.OFF)

  override def onClose(): Unit = {
    cameraPreview.setFlashMode(FlashMode.OFF) //set back to default off when leaving camera

    getControllerFactory.getCameraController.closeCamera(cameraContext)
  }

  override def onTakePhoto(): Unit = {
    if (cameraContext != CameraContext.SIGN_UP) previewProgressBar.foreach(_.setVisible(true))
    cameraTopControl.foreach(c => ViewUtils.fadeOutView(c, cameraControlAnimationDuration))
    cameraPreview.takePicture()
  }

  override def onOpenImageGallery(): Unit = {
    openGallery()
  }

  override def onCancelPreview(): Unit = {
    previewProgressBar.foreach(_.setVisible(false))

    imagePreviewContainer.foreach { c =>
      val animator: ObjectAnimator = ObjectAnimator.ofFloat(c, View.ALPHA, 1, 0)
      animator.setDuration(cameraControlAnimationDuration)
      animator.addListener(new AnimatorListenerAdapter() {
        override def onAnimationCancel(animation: Animator): Unit = hideImagePreviewOnAnimationEnd()
        override def onAnimationEnd(animation: Animator): Unit = hideImagePreviewOnAnimationEnd()
      })
      animator.start()
    }

    showCameraFeed()
  }

  override def onSketchOnPreviewPicture(imageAsset: ImageAsset, source: ImagePreviewLayout.Source, method: IDrawingController.DrawingMethod): Unit = {
    getControllerFactory.getDrawingController.showDrawing(imageAsset, IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW)
  }

  override def onSendPictureFromPreview(imageAsset: ImageAsset, source: ImagePreviewLayout.Source): Unit = {
    getControllerFactory.getCameraController.onBitmapSelected(imageAsset, cameraContext)
  }

  private def showPreview(setImage: (ImagePreviewLayout) => Unit) = {
    hideCameraFeed()

    previewProgressBar.foreach(_.setVisible(false))

    imagePreviewContainer.foreach { c =>
      c.removeAllViews()
      c.addView(returning(ImagePreviewLayout.newInstance(getContext, imagePreviewContainer.get, this)) { layout =>
        setImage(layout)
        layout.showSketch(cameraContext == CameraContext.MESSAGE)
        layout.showTitle(cameraContext == CameraContext.MESSAGE)
      })
      c.setVisible(true)
      ObjectAnimator.ofFloat(c, View.ALPHA, 0, 1).setDuration(cameraPreviewAnimationDuration).start()
    }

    cameraBottomControl.foreach(_.setVisible(false))
  }

  private def hideImagePreviewOnAnimationEnd(): Unit = {
    imagePreviewContainer.foreach(_.setVisible(false))
    cameraBottomControl.foreach(_.setVisible(true))
  }

  private def showCameraFeed(): Unit = {
    cameraTopControl.foreach(c => ViewUtils.fadeInView(c, cameraControlAnimationDuration))
    cameraPreview.foreach(_.setVisible(true))
    cameraBottomControl.foreach(_.enableShutterButton())
  }

  private def hideCameraFeed(): Unit = {
    cameraTopControl.foreach(c => ViewUtils.fadeOutView(c, cameraControlAnimationDuration))
    if (cameraPreview != null) cameraPreview.setVisibility(View.GONE)
  }

  override def onOrientationHasChanged(squareOrientation: SquareOrientation): Unit = {
    cameraTopControl.setConfigOrientation(squareOrientation)
    cameraBottomControl.foreach(_.setConfigOrientation(squareOrientation))
  }

  override def onAccentColorHasChanged(sender: Any, color: Int): Unit =
    previewProgressBar.foreach(_.setTextColor(color))

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    intentsManager.onActivityResult(requestCode, resultCode, data)
  }

  private def processGalleryImage(uri: URI): Unit = {
    hideCameraFeed()
    if (cameraContext != CameraContext.SIGN_UP) previewProgressBar.foreach(_.setVisible(true))
    showPreview { _.setImage(uri, ImagePreviewLayout.Camera) }
  }
}


object CameraFragment2 {
  val TAG: String = classOf[CameraFragment2].getName
  private val CAMERA_CONTEXT: String = "CAMERA_CONTEXT"
  private val SHOW_GALLERY: String = "SHOW_GALLERY"
  private val ALREADY_OPENED_GALLERY: String = "ALREADY_OPENED_GALLERY"

  def newInstance(cameraContext: CameraContext): CameraFragment2 = newInstance(cameraContext, showGallery = false)

  def newInstance(cameraContext: CameraContext, showGallery: Boolean): CameraFragment2 =
    returning(new CameraFragment2) {
      _.setArguments(returning(new Bundle) { bundle =>
        bundle.putInt(CAMERA_CONTEXT, cameraContext.ordinal)
        bundle.putBoolean(SHOW_GALLERY, showGallery)
      })
    }

  trait Container extends CameraActionObserver {}

}
