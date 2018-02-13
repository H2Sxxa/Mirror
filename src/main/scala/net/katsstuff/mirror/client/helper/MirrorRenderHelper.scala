/*
 * This file is part of Mirror, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 TeamNightclipse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package net.katsstuff.mirror.client.helper

import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.{Cylinder, Disk, Sphere}

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.{GLAllocation, GlStateManager, OpenGlHelper, Tessellator}
import net.minecraft.client.resources.{IResourceManagerReloadListener, SimpleReloadableResourceManager}
import net.minecraft.util.math.MathHelper
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

//Many render methods adopted from GLU classes
@SideOnly(Side.CLIENT)
object MirrorRenderHelper {

  private var sphereHighId   = 0
  private var sphereMidId    = 0
  private var sphereLowId    = 0

  private var cylinderHighId = 0
  private var cylinderMidId  = 0
  private var cylinderLowId  = 0

  private var coneHighId     = 0
  private var coneMidId      = 0
  private var coneLowId      = 0

  private var diskHighId     = 0
  private var diskMidId      = 0
  private var diskLowId      = 0

  private val useVBO = OpenGlHelper.useVbo()

  def bakeModels(): Unit = {
    val tes = Tessellator.getInstance()
    val vb  = tes.getBuffer

    val sphere   = new Sphere
    val cylinder = new Cylinder
    val cone     = new Cylinder
    val disk     = new Disk

    sphereHighId = createList {
      GlStateManager.rotate(90F, 1F, 0F, 0F)
      sphere.draw(1F, 32, 16)
      GlStateManager.rotate(-90F, 1F, 0F, 0F)
    }
    sphereMidId = createList {
      GlStateManager.rotate(90F, 1F, 0F, 0F)
      sphere.draw(1F, 16, 8)
      GlStateManager.rotate(-90F, 1F, 0F, 0F)
    }
    sphereLowId = createList {
      GlStateManager.rotate(90F, 1F, 0F, 0F)
      sphere.draw(1F, 8, 4)
      GlStateManager.rotate(-90F, 1F, 0F, 0F)
    }

    cylinderHighId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cylinder.draw(1F, 1F, 1F, 32, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }
    cylinderMidId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cylinder.draw(1F, 1F, 1F, 16, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }
    cylinderLowId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cylinder.draw(1F, 1F, 1F, 8, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }

    coneHighId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cone.draw(1F, 0F, 1F, 32, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }
    coneMidId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cone.draw(1F, 0F, 1F, 16, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }
    coneLowId = createList {
      GlStateManager.translate(0F, 0F, -0.5F)
      cone.draw(1F, 0F, 1F, 8, 1)
      GlStateManager.translate(0F, 0F, 0.5F)
    }

    diskHighId = createList(disk.draw(1F, 0F, 32, 1))
    diskMidId = createList(disk.draw(1F, 0F, 16, 1))
    diskLowId = createList(disk.draw(1F, 0F, 8, 1))
  }

  def createList(create: => Unit): Int = {
    val res = GLAllocation.generateDisplayLists(1)
    GlStateManager.glNewList(res, GL11.GL_COMPILE)

    create

    GlStateManager.glEndList()
    res
  }

  private def drawObj(color: Int, alpha: Float, dist: Double, highId: Int, midId: Int, lowId: Int): Unit = {
    val r = (color >> 16 & 255) / 255F
    val g = (color >> 8 & 255) / 255F
    val b = (color & 255) / 255F
    GlStateManager.color(r, g, b, alpha)

    val id =
      if (dist < 8 * 8) highId
      else if (dist < 32 * 32) midId
      else lowId
    GlStateManager.callList(id)
  }

  def drawSphere(color: Int, alpha: Float, dist: Double): Unit =
    drawObj(color, alpha, dist, sphereHighId, sphereMidId, sphereLowId)

  def drawCylinder(color: Int, alpha: Float, dist: Double): Unit =
    drawObj(color, alpha, dist, cylinderHighId, cylinderMidId, cylinderLowId)

  def drawCone(color: Int, alpha: Float, dist: Double): Unit =
    drawObj(color, alpha, dist, coneHighId, coneMidId, coneLowId)

  def drawDisk(color: Int, alpha: Float, dist: Double): Unit =
    drawObj(color, alpha, dist, diskHighId, diskMidId, diskLowId)

  def renderDropOffSphere(
      radius: Float,
      slices: Int,
      stacks: Int,
      dropOffRate: Float,
      color: Int,
      alpha: Float
  ): Unit = {
    val r = (color >> 16 & 255) / 255F
    val g = (color >> 8 & 255) / 255F
    val b = (color & 255) / 255F

    val tes = Tessellator.getInstance
    val bb  = tes.getBuffer

    val drho   = (Math.PI / stacks).toFloat
    val dtheta = (2F * Math.PI / slices).toFloat

    GlStateManager.disableCull()
    GlStateManager.depthMask(false)

    bb.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR)
    bb.pos(0F, 0F, radius).color(r, g, b, alpha).endVertex()
    for (j <- 0 to slices) {
      val theta =
        if (j == slices) 0.0f
        else j * dtheta
      val x = -MathHelper.sin(theta) * MathHelper.sin(drho)
      val y = MathHelper.cos(theta) * MathHelper.sin(drho)
      val z = MathHelper.cos(drho)
      bb.pos(x * radius, y * radius, z * radius).color(r, g, b, alpha).endVertex()
    }
    tes.draw()

    val imin = 1
    val imax = stacks - 1

    for (i <- imin until imax) {
      val newAlpha = Math.max(alpha - i * dropOffRate, 0F)
      val rho      = i * drho
      bb.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR)
      for (j <- 0 to slices) {
        val theta = if (j == slices) 0.0f else j * dtheta
        var x     = -MathHelper.sin(theta) * MathHelper.sin(rho)
        var y     = MathHelper.cos(theta) * MathHelper.sin(rho)
        var z     = MathHelper.cos(rho)
        bb.pos(x * radius, y * radius, z * radius).color(r, g, b, newAlpha).endVertex()
        x = -MathHelper.sin(theta) * MathHelper.sin(rho + drho)
        y = MathHelper.cos(theta) * MathHelper.sin(rho + drho)
        z = MathHelper.cos(rho + drho)
        bb.pos(x * radius, y * radius, z * radius).color(r, g, b, newAlpha).endVertex()
      }
      tes.draw()
    }

    GlStateManager.enableCull()
    GlStateManager.depthMask(true)
  }

  def registerResourceReloadListener(listener: IResourceManagerReloadListener): Unit = {
    Minecraft.getMinecraft.getResourceManager match {
      case resourceManager: SimpleReloadableResourceManager => resourceManager.registerReloadListener(listener)
      case _                                                => listener.onResourceManagerReload(Minecraft.getMinecraft.getResourceManager)
    }
  }
}
