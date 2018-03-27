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
package com.waz.zclient.messages.parts

import java.io.File

import android.support.v7.widget.RecyclerView
import com.waz.api.ImageAssetFactory
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.images.ImageAssetView

class GalleryItemViewHolder(imageView: ImageAssetView) extends RecyclerView.ViewHolder(imageView) {

  def bind(path: String, callback: CursorImagesLayout.Callback): Unit = {
    val uri = AndroidURIUtil.fromFile(new File(path))
    val asset = ImageAssetFactory.getImageAsset(uri)
    imageView.setImageAsset(asset)
    imageView.onClick(callback.onGalleryPictureSelected(asset))
  }

}
