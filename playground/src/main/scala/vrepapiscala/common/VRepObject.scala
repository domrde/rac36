package vrepapiscala.common

import coppelia.{FloatWA, remoteApi}
import vrepapiscala.OpMode
import vrepapiscala.VRepAPI.checkReturnCode

/**
  * Created by dda on 06.05.17.
  */
abstract class VRepObject {
  private[vrepapiscala] val remote: remoteApi

  private[vrepapiscala] val id: Int

  private[vrepapiscala] val handle: Int

  private[vrepapiscala] val opMode: OpMode

  def absolutePosition: Vec3 = {
    getRelativePosition(id, handle, -1, opMode)
  }

  def positionRelativeTo(relativeTo: VRepObject): Vec3 = {
    getRelativePosition(id, handle, relativeTo.handle, opMode)
  }

  private[vrepapiscala] def getRelativePosition(id: Int, handle: Int, relativeToHandle: Int, opMode: OpMode): Vec3 = {
    val pos = new FloatWA(3)
    checkReturnCode(remote.simxGetObjectPosition(
      id, handle, relativeToHandle, pos, opMode.rawCode))
    val ps = pos.getArray
    Vec3(ps(0), ps(1), ps(2))
  }
}
