package net.katsstuff.mirror.client.baked

import java.util

import net.katsstuff.mirror.client.ClientProxy
import net.katsstuff.mirror.data.{MutableVector3, Quat, Vector3}
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.util.EnumFacing
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.util.vector.Vector4f

import scala.annotation.varargs
import scala.collection.JavaConverters._

@SideOnly(Side.CLIENT)
object QuadBuilder {
  def apply(format: VertexFormat):      QuadBuilder = new QuadBuilder(format)
  def withFormat(format: VertexFormat): QuadBuilder = new QuadBuilder(format)
}
@SideOnly(Side.CLIENT)
class QuadBuilder private (
    private[this] val format: VertexFormat,
    private[this] val hasBrightness: Boolean = false,
    private[this] val from: Vector3 = Vector3.Zero,
    private[this] val to: Vector3 = Vector3.Zero,
    private[this] val last: EnumFacing = null,
    private[this] val facingMap: Map[EnumFacing, QuadHolder] = Map.empty
) {

  private def copy(
      hasBrightness: Boolean = hasBrightness,
      from: Vector3 = from,
      to: Vector3 = to,
      last: EnumFacing = last,
      facingMap: Map[EnumFacing, QuadHolder] = facingMap
  ): QuadBuilder = new QuadBuilder(format, hasBrightness, from, to, last, facingMap)

  def setFrom(x: Double, y: Double, z: Double): QuadBuilder = copy(from = Vector3(x, y, z))

  def setTo(x: Double, y: Double, z: Double): QuadBuilder = copy(to = Vector3(x, y, z))

  def setHasBrightness(hasBrightness: Boolean): QuadBuilder = copy(hasBrightness = hasBrightness)

  def addAll(sprite: TextureAtlasSprite): QuadBuilder =
    EnumFacing.values.foldLeft(this)((builder, facing) => builder.addFace(facing, 0F, 16F, 0F, 16F, sprite))

  def addAll(uMin: Float, uMax: Float, vMin: Float, vMax: Float, sprite: TextureAtlasSprite): QuadBuilder =
    EnumFacing.values.foldLeft(this)((builder, facing) => builder.addFace(facing, uMin, uMax, vMin, vMax, sprite))

  def addFace(
      facing: EnumFacing,
      uMin: Float,
      uMax: Float,
      vMin: Float,
      vMax: Float,
      sprite: TextureAtlasSprite
  ): QuadBuilder = {
    import EnumFacing._
    val (a, b, c, d) = facing match {
      case DOWN =>
        (
          Vector3(to.x, from.y, from.z),
          Vector3(to.x, from.y, to.z),
          Vector3(from.x, from.y, to.z),
          Vector3(from.x, from.y, from.z)
        )
      case UP =>
        (
          Vector3(from.x, to.y, from.z),
          Vector3(from.x, to.y, to.z),
          Vector3(to.x, to.y, to.z),
          Vector3(to.x, to.y, from.z)
        )
      case NORTH =>
        (
          Vector3(to.x, from.y, to.z),
          Vector3(to.x, to.y, to.z),
          Vector3(from.x, to.y, to.z),
          Vector3(from.x, from.y, to.z)
        )
      case SOUTH =>
        (
          Vector3(from.x, from.y, from.z),
          Vector3(from.x, to.y, from.z),
          Vector3(to.x, to.y, from.z),
          Vector3(to.x, from.y, from.z)
        )
      case WEST =>
        (
          Vector3(from.x, from.y, to.z),
          Vector3(from.x, to.y, to.z),
          Vector3(from.x, to.y, from.z),
          Vector3(from.x, from.y, from.z)
        )
      case EAST =>
        (
          Vector3(to.x, from.y, from.z),
          Vector3(to.x, to.y, from.z),
          Vector3(to.x, to.y, to.z),
          Vector3(to.x, from.y, to.z)
        )
    }

    val uv     = new Vector4f(uMin, uMax, vMin, vMax)
    val holder = QuadHolder(sprite, a / 16D, b / 16D, c / 16D, d / 16D, uv)
    copy(facingMap = facingMap.updated(facing, holder), last = facing)
  }

  def mirror: QuadBuilder = {
    facingMap.get(last) match {
      case Some(holder) => {
        val quat = Quat.fromAxisAngle(last.getAxis, 180D)
        copy(facingMap = facingMap.updated(last, holder.rotate(quat)))
      }
      case None         => this
    }
  }

  def clear: QuadBuilder = copy(facingMap = Map.empty)

  @varargs def rotate(facing: EnumFacing, exclude: EnumFacing*): QuadBuilder = {
    if (!exclude.contains(facing)) {
      import EnumFacing._
      facing match {
        case DOWN =>
          rotate(EnumFacing.Axis.X, 180F)
        case UP =>
          rotate(EnumFacing.Axis.X, 180F)
        case _ =>
          rotate(EnumFacing.Axis.X, 90F)
            .rotate(EnumFacing.Axis.Y, -facing.getHorizontalAngle)
            .rotate(EnumFacing.Axis.Y, -90F)
      }
    } else this
  }

  def rotate(axis: EnumFacing.Axis, angle: Float): QuadBuilder = {
    val quat = Quat.fromAxisAngle(axis, angle)
    val newMap = facingMap.map(e => e._1 -> e._2.rotate(quat))
    copy(facingMap = newMap)
  }

  def bake: Seq[BakedQuad] = facingMap.map(e => createQuad(e._2, e._1)).toSeq

  def bakeJava: util.List[BakedQuad] = bake.asJava

  private def createQuad(holder: QuadHolder, facing: EnumFacing): UnpackedBakedQuad = {
    val uv     = holder.uv
    val a      = holder.a
    val b      = holder.b
    val c      = holder.c
    val d      = holder.d
    val normal = (c.asMutable -= b).crossMutable(a - b).normalizeMutable

    val builder = new UnpackedBakedQuad.Builder(format)
    putVertex(builder, normal, a.x, a.y, a.z, holder.sprite, uv.y, uv.w, hasBrightness)
    putVertex(builder, normal, b.x, b.y, b.z, holder.sprite, uv.y, uv.z, hasBrightness)
    putVertex(builder, normal, c.x, c.y, c.z, holder.sprite, uv.x, uv.z, hasBrightness)
    putVertex(builder, normal, d.x, d.y, d.z, holder.sprite, uv.x, uv.w, hasBrightness)
    builder.setQuadOrientation(facing)
    builder.setTexture(holder.sprite)
    builder.build
  }

  private def putVertex(
      builder: UnpackedBakedQuad.Builder,
      normal: MutableVector3,
      x: Double,
      y: Double,
      z: Double,
      sprite: TextureAtlasSprite,
      u: Float,
      v: Float,
      hasBrightness: Boolean
  ): Unit = {
    var usedU = u
    var usedV = v
    for (e <- 0 until format.getElementCount) {
      import net.minecraft.client.renderer.vertex.VertexFormatElement.EnumUsage._
      format.getElement(e).getUsage match {
        case POSITION =>
          builder.put(e, x.toFloat, y.toFloat, z.toFloat, 1F)
        case COLOR =>
          builder.put(e, 1F, 1F, 1F, 1F)
        case UV =>
          if (!ClientProxy.isOptifineInstalled && (format.getElement(e).getIndex == 1)) {
            if (hasBrightness) builder.put(e, 1F, 1F) else builder.put(e, 0F, 0F)
          } else if (format.getElement(e).getIndex == 0) {
            usedU = sprite.getInterpolatedU(usedU)
            usedV = sprite.getInterpolatedV(usedV)
            builder.put(e, usedU, usedV, 0F, 1F)
          }
        case NORMAL =>
          if (!ClientProxy.isOptifineInstalled && hasBrightness) builder.put(e, 0F, 1F, 0F)
          else builder.put(e, normal.x.toFloat, normal.y.toFloat, normal.z.toFloat)
        case _ =>
          builder.put(e)
      }
    }
  }
}

private case class QuadHolder(
    private[baked] var sprite: TextureAtlasSprite,
    private[baked] var a: Vector3,
    private[baked] var b: Vector3,
    private[baked] var c: Vector3,
    private[baked] var d: Vector3,
    private[baked] var uv: Vector4f
) {

  private[baked] def getVectors: Array[Vector3] = Array(a, b, c, d)

  private[baked] def mapVectors(f: Vector3 => Vector3): QuadHolder = QuadHolder(sprite, f(a), f(b), f(c), f(d), uv)

  //TODO: Can we instead rotate by a quat made from offsetting the axis here, and save computations?
  private[baked] def rotate(quat: Quat): QuadHolder = mapVectors { vec =>
    vec.asMutable.subtractMutable(0.5D).rotate(quat).addMutable(0.5D).asImmutable
  }
}
