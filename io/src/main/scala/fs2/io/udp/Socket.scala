package fs2
package io
package udp

import scala.concurrent.duration.FiniteDuration

import java.net.{InetAddress, InetSocketAddress, NetworkInterface}

/**
  * Provides the ability to read/write from a UDP socket in the effect `F`.
  *
  * To construct a `Socket`, use the methods in the [[fs2.io.udp]] package object.
  */
trait Socket[F[_]] {
  /**
    * Reads a single packet from this udp socket.
    *
    * If `timeout` is specified, then resulting `F` will fail with `java.nio.channels.InterruptedByTimeoutException`
    * if read was not satisfied in given timeout.
    */
  def read(timeout: Option[FiniteDuration] = None): F[Packet]

  /**
    * Reads packets received from this udp socket.
    *
    * Note that multiple `reads` may execute at same time, causing each evaluation to receive fair
    * amount of messages.
    *
    * If `timeout` is specified, then resulting stream will fail with `java.nio.channels.InterruptedByTimeoutException`
    * if a read was not satisfied in given timeout.
    *
    * @return stream of packets
    */
  def reads(timeout: Option[FiniteDuration] = None): Stream[F, Packet]

  /**
    * Write a single packet to this udp socket.
    *
    * If `timeout` is specified, then resulting `F` will fail with `java.nio.channels.InterruptedByTimeoutException`
    * if write was not completed in given timeout.
    *
    * @param packet  Packet to write
    */
  def write(packet: Packet, timeout: Option[FiniteDuration] = None): F[Unit]

  /**
    * Writes supplied packets to this udp socket.
    *
    * If `timeout` is specified, then resulting pipe will fail with `java.nio.channels.InterruptedByTimeoutException`
    * if a write was not completed in given timeout.
    */
  def writes(timeout: Option[FiniteDuration] = None): Pipe[F, Packet, Unit]

  /** Returns the local address of this udp socket. */
  def localAddress: F[InetSocketAddress]

  /** Closes this socket. */
  def close: F[Unit]

  /**
    * Joins a multicast group on a specific network interface.
    *
    * @param group address of group to join
    * @param interface network interface upon which to listen for datagrams
    */
  def join(group: InetAddress, interface: NetworkInterface): F[AnySourceGroupMembership]

  /**
    * Joins a source specific multicast group on a specific network interface.
    *
    * @param group address of group to join
    * @param interface network interface upon which to listen for datagrams
    * @param source limits received packets to those sent by the source
    */
  def join(group: InetAddress, interface: NetworkInterface, source: InetAddress): F[GroupMembership]

  /** Result of joining a multicast group on a UDP socket. */
  trait GroupMembership {
    /** Leaves the multicast group, resulting in no further packets from this group being read. */
    def drop: F[Unit]
  }

  /** Result of joining an any-source multicast group on a UDP socket. */
  trait AnySourceGroupMembership extends GroupMembership {
    /** Blocks packets from the specified source address. */
    def block(source: InetAddress): F[Unit]

    /** Unblocks packets from the specified source address. */
    def unblock(source: InetAddress): F[Unit]
  }
}
