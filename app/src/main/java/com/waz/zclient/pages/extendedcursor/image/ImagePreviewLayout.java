/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.extendedcursor.image;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.R;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.pages.main.profile.views.ConfirmationMenu;
import com.waz.zclient.pages.main.profile.views.ConfirmationMenuListener;
import com.waz.zclient.ui.theme.OptionsDarkTheme;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetView;

public class ImagePreviewLayout extends FrameLayout implements
                                                            ConfirmationMenuListener,
                                                            View.OnClickListener {

    public enum Source {
        IN_APP_GALLERY,
        DEVICE_GALLERY,
        CAMERA
    }

    private ConfirmationMenu approveImageSelectionMenu;
    private ImageAssetView imageView;
    private FrameLayout titleTextViewContainer;
    private TextView titleTextView;
    private View sketchMenuContainer;
    private View sketchDrawButton;
    private View sketchEmojiButton;
    private View sketchTextButton;
    private boolean sketchShouldBeVisible;

    private Callback callback;
    private ImageAsset imageAsset;
    private Source source;

    public ImagePreviewLayout(Context context) {
        this(context, null);
    }

    public ImagePreviewLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImagePreviewLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static ImagePreviewLayout newInstance(Context context, ViewGroup container, Callback callback) {
        final ImagePreviewLayout imagePreviewLayout = (ImagePreviewLayout) LayoutInflater.from(context).inflate(
            R.layout.fragment_cursor_images_preview,
            container,
            false);
        imagePreviewLayout.callback = callback;
        return imagePreviewLayout;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // eats the click
        setOnClickListener(this);
        imageView = ViewUtils.getView(this, R.id.iv__conversation__preview);
        approveImageSelectionMenu = ViewUtils.getView(this, R.id.cm__cursor_preview);
        sketchMenuContainer = ViewUtils.getView(this, R.id.ll__preview__sketch);
        sketchDrawButton =  ViewUtils.getView(this, R.id.gtv__preview__drawing_button__sketch);
        sketchEmojiButton =  ViewUtils.getView(this, R.id.gtv__preview__drawing_button__emoji);
        sketchTextButton =  ViewUtils.getView(this, R.id.gtv__preview__drawing_button__text);
        titleTextView = ViewUtils.getView(this, R.id.ttv__image_preview__title);
        titleTextViewContainer = ViewUtils.getView(this, R.id.ttv__image_preview__title__container);

        imageView.setOnClickListener(this);

        approveImageSelectionMenu.setWireTheme(new OptionsDarkTheme(getContext()));
        approveImageSelectionMenu.setCancel(getResources().getString(R.string.confirmation_menu__cancel));
        approveImageSelectionMenu.setConfirm(getResources().getString(R.string.confirmation_menu__confirm_done));
        approveImageSelectionMenu.setConfirmationMenuListener(this);

        sketchDrawButton.setOnClickListener(this);
        sketchEmojiButton.setOnClickListener(this);
        sketchTextButton.setOnClickListener(this);
        // By default sketch button is visible
        sketchShouldBeVisible = true;
    }

    @Override
    public void confirm() {
        callback.onSendPictureFromPreview(imageAsset, source);
    }

    @Override
    public void cancel() {
        callback.onCancelPreview();
    }

    public void setImage(ImageAsset imageAsset, Source source) {
        this.source = source;
        this.imageAsset = imageAsset;
        imageView.setImageAsset(imageAsset);
    }

    public void setImage(byte[] imageData, boolean isMirrored) {
        this.source = Source.CAMERA;
        this.imageAsset = ImageAssetFactory.getImageAsset(imageData);
        this.imageAsset.setMirrored(isMirrored);
        imageView.setImageAsset(this.imageAsset);
    }

    public void setImage(URI uri, Source source) {
        this.source = source;
        this.imageAsset = ImageAssetFactory.getImageAsset(uri);
        imageView.setImageAsset(this.imageAsset);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv__conversation__preview:
                if (approveImageSelectionMenu.getVisibility() == VISIBLE) {
                    if (sketchShouldBeVisible) {
                        ViewUtils.fadeOutView(sketchMenuContainer);
                    }
                    ViewUtils.fadeOutView(approveImageSelectionMenu);

                    if (!TextUtils.isEmpty(titleTextView.getText())) {
                        ViewUtils.fadeOutView(titleTextViewContainer);
                    }
                } else {
                    if (sketchShouldBeVisible) {
                        ViewUtils.fadeInView(sketchMenuContainer);
                    }
                    ViewUtils.fadeInView(approveImageSelectionMenu);

                    if (!TextUtils.isEmpty(titleTextView.getText())) {
                        ViewUtils.fadeInView(titleTextViewContainer);
                    }
                }
                break;
            case R.id.gtv__preview__drawing_button__sketch:
                if (callback != null) {
                    callback.onSketchOnPreviewPicture(imageAsset,
                                                      source,
                                                      IDrawingController.DrawingMethod.DRAW);
                }
                break;
            case R.id.gtv__preview__drawing_button__emoji:
                if (callback != null) {
                    callback.onSketchOnPreviewPicture(imageAsset,
                                                      source,
                                                      IDrawingController.DrawingMethod.EMOJI);
                }
                break;
            case R.id.gtv__preview__drawing_button__text:
                if (callback != null) {
                    callback.onSketchOnPreviewPicture(imageAsset,
                                                      source,
                                                      IDrawingController.DrawingMethod.TEXT);
                }
                break;
        }
    }

    public void setAccentColor(int color) {
        approveImageSelectionMenu.setAccentColor(color);
    }


    public void showSketch(boolean show) {
        sketchShouldBeVisible = show;
        sketchMenuContainer.setVisibility(show ? VISIBLE : GONE);
    }

    public void setTitle(String title) {
        titleTextView.setText(title);
        titleTextViewContainer.setVisibility(TextUtils.isEmpty(titleTextView.getText()) ? GONE : VISIBLE);
    }

    public interface Callback {
        void onCancelPreview();

        void onSketchOnPreviewPicture(ImageAsset imageAsset, Source source, IDrawingController.DrawingMethod method);

        void onSendPictureFromPreview(ImageAsset imageAsset, Source source);
    }

}
