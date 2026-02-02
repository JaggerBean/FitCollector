/* eslint-disable react/no-unknown-property */
import { useEffect, useRef, useState } from "react";
import { Canvas, extend, useFrame } from "@react-three/fiber";
import { Environment, Lightformer, useGLTF, useTexture } from "@react-three/drei";
import {
  BallCollider,
  CuboidCollider,
  Physics,
  RigidBody,
  type RigidBodyProps,
  useRopeJoint,
  useSphericalJoint,
} from "@react-three/rapier";
import { MeshLineGeometry, MeshLineMaterial } from "meshline";
import * as THREE from "three";

import cardGLB from "../assets/lanyard/card.glb";
import lanyardTexture from "../assets/lanyard/stepcraft-lanyard.png";

extend({ MeshLineGeometry, MeshLineMaterial });

type LanyardProps = {
  position?: [number, number, number];
  gravity?: [number, number, number];
  fov?: number;
  transparent?: boolean;
  cardData?: {
    serverName: string;
    apiKey: string;
    inviteCode?: string | null;
    ownerEmail: string;
  };
};

export default function Lanyard({
  position = [0, -3.0, 22],
  gravity = [0, -40, 0],
  fov = 24,
  transparent = true,
  cardData,
}: LanyardProps) {
  const [isMobile, setIsMobile] = useState<boolean>(() => window.innerWidth < 768);

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768);
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return (
    <div className="relative h-screen w-full">
      <Canvas
        camera={{ position, fov }}
        dpr={[1, isMobile ? 1.5 : 2]}
        gl={{ alpha: transparent }}
        onCreated={({ gl }) => gl.setClearColor(new THREE.Color(0x000000), transparent ? 0 : 1)}
      >
        <ambientLight intensity={Math.PI} />
        <Physics gravity={gravity} timeStep={isMobile ? 1 / 30 : 1 / 60}>
          <Band isMobile={isMobile} cardData={cardData} />
        </Physics>
        <Environment blur={0.75}>
          <Lightformer
            intensity={2}
            color="white"
            position={[0, -1, 5]}
            rotation={[0, 0, Math.PI / 3]}
            scale={[100, 0.1, 1]}
          />
          <Lightformer
            intensity={3}
            color="white"
            position={[-1, -1, 1]}
            rotation={[0, 0, Math.PI / 3]}
            scale={[100, 0.1, 1]}
          />
          <Lightformer
            intensity={3}
            color="white"
            position={[1, 1, 1]}
            rotation={[0, 0, Math.PI / 3]}
            scale={[100, 0.1, 1]}
          />
          <Lightformer
            intensity={10}
            color="white"
            position={[-10, 0, 14]}
            rotation={[0, Math.PI / 2, Math.PI / 3]}
            scale={[100, 10, 1]}
          />
        </Environment>
      </Canvas>
    </div>
  );
}

type BandProps = {
  maxSpeed?: number;
  minSpeed?: number;
  isMobile?: boolean;
  cardData?: {
    serverName: string;
    apiKey: string;
    inviteCode?: string | null;
    ownerEmail: string;
  };
};

function Band({ maxSpeed = 50, minSpeed = 0, isMobile = false, cardData }: BandProps) {
  const band = useRef<any>(null);
  const fixed = useRef<any>(null);
  const j1 = useRef<any>(null);
  const j2 = useRef<any>(null);
  const j3 = useRef<any>(null);
  const card = useRef<any>(null);

  const vec = new THREE.Vector3();
  const ang = new THREE.Vector3();
  const rot = new THREE.Vector3();
  const dir = new THREE.Vector3();

  const segmentProps: any = {
    type: "dynamic" as RigidBodyProps["type"],
    canSleep: true,
    colliders: false,
    angularDamping: 4,
    linearDamping: 4,
  };

  const { nodes, materials } = useGLTF(cardGLB) as any;
  const texture = useTexture(lanyardTexture);
  const logoTexture = useTexture("/logo.png");
  const badgeTexture = useState(() => new THREE.CanvasTexture(document.createElement("canvas")))[0];
  const badgeBackTexture = useState(() => new THREE.CanvasTexture(document.createElement("canvas")))[0];
  const [curve] = useState(
    () =>
      new THREE.CatmullRomCurve3([
        new THREE.Vector3(),
        new THREE.Vector3(),
        new THREE.Vector3(),
        new THREE.Vector3(),
      ]),
  );
  const [dragged, drag] = useState<false | THREE.Vector3>(false);
  const [hovered, hover] = useState(false);

  useEffect(() => {
    if (!cardData || !logoTexture.image) return;
    const content = document.createElement("canvas");
    content.width = 1024;
    content.height = 1536;
    const ctx = content.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, content.width, content.height);
    ctx.fillStyle = "#f2f3f6";
    ctx.fillRect(0, 0, content.width, content.height);

    const cardW = content.width * 0.96;
    const cardH = content.height * 0.9;
    const cardX = (content.width - cardW) / 2;
    const cardY = content.height * 0.05;

    ctx.save();
    ctx.beginPath();
    roundRect(ctx, cardX, cardY, cardW, cardH, 100);
    ctx.clip();
    const gradient = ctx.createLinearGradient(cardX, cardY, cardX, cardY + cardH);
    gradient.addColorStop(0, "#62c3ff");
    gradient.addColorStop(0.4, "#add9ff");
    gradient.addColorStop(0.7, "#e0ffbf");
    gradient.addColorStop(1, "rgb(144, 255, 136)");
    ctx.fillStyle = gradient;
    ctx.fillRect(cardX - 2, cardY - 2, cardW + 4, cardH + 4);

    const logo = logoTexture.image as HTMLImageElement;
    const maxLogoW = cardW * 0.38;
    const maxLogoH = cardH * 0.24;
    const logoScale = Math.min(maxLogoW / logo.width, maxLogoH / logo.height);
    const logoW = logo.width * logoScale;
    const logoH = logo.height * logoScale;
    const logoX = cardX + (cardW - logoW) / 2;
    const logoY = cardY + cardH * 0.07;
    ctx.drawImage(logo, logoX, logoY, logoW, logoH);

    const centerX = cardX + cardW / 2;
    let y = cardY + cardH * 0.38;

    ctx.textAlign = "center";
    ctx.fillStyle = "#0a1424";
    ctx.font = "800 96px 'Segoe UI', Arial, sans-serif";
    const headline = cardData.serverName || "Server Registered!";
    ctx.fillText(headline, centerX, y);

    y += 110;
    ctx.fillStyle = "#1c6fd1";
    ctx.font = "700 70px 'Segoe UI', Arial, sans-serif";
    ctx.fillText("Welcome to StepCraft", centerX, y);

    y += 100;
    ctx.fillStyle = "#20304a";
    ctx.font = "600 44px 'Segoe UI', Arial, sans-serif";
    const line1 = "Your server is ready to sync steps";
    const line2 = "and rewards with your players.";
    ctx.fillText(line1, centerX, y);
    y += 58;
    ctx.fillText(line2, centerX, y);

    y += 72;
    ctx.fillStyle = "#1d2d44";
    ctx.font = "600 40px 'Segoe UI', Arial, sans-serif";
    const emailLine = "Check your email for your new credentials.";
    ctx.fillText(emailLine, centerX, y);

    ctx.restore();

    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 1536;
    const ctx2 = canvas.getContext("2d");
    if (!ctx2) return;

    // Duplicate horizontally to match split UVs (front/back)
    ctx2.drawImage(content, 0, 0, canvas.width / 2, canvas.height);
    ctx2.drawImage(content, canvas.width / 2, 0, canvas.width / 2, canvas.height);

    badgeTexture.image = canvas;
    badgeTexture.needsUpdate = true;
    badgeTexture.flipY = false;
    badgeTexture.colorSpace = THREE.SRGBColorSpace;
    badgeTexture.wrapS = THREE.ClampToEdgeWrapping;
    badgeTexture.wrapT = THREE.ClampToEdgeWrapping;

    badgeBackTexture.image = canvas;
    badgeBackTexture.needsUpdate = true;
    badgeBackTexture.flipY = false;
    badgeBackTexture.colorSpace = THREE.SRGBColorSpace;
    badgeBackTexture.repeat.set(-1, 1);
    badgeBackTexture.offset.set(1, 0);
    badgeBackTexture.wrapS = THREE.ClampToEdgeWrapping;
    badgeBackTexture.wrapT = THREE.ClampToEdgeWrapping;
  }, [cardData, logoTexture, badgeTexture, badgeBackTexture]);

  function roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    height: number,
    radius: number
  ) {
    const r = Math.min(radius, width / 2, height / 2);
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + width - r, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + r);
    ctx.lineTo(x + width, y + height - r);
    ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
    ctx.lineTo(x + r, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
  }

  useRopeJoint(fixed, j1, [[0, 0, 0], [0, 0, 0], 1.8]);
  useRopeJoint(j1, j2, [[0, 0, 0], [0, 0, 0], 1.8]);
  useRopeJoint(j2, j3, [[0, 0, 0], [0, 0, 0], 1.8]);
  useSphericalJoint(j3, card, [
    [0, 0, 0],
    [0, 1.98, 0],
  ]);

  useEffect(() => {
    if (hovered) {
      document.body.style.cursor = dragged ? "grabbing" : "grab";
      return () => {
        document.body.style.cursor = "auto";
      };
    }
  }, [hovered, dragged]);

  useFrame((state, delta) => {
    if (dragged && typeof dragged !== "boolean") {
      vec.set(state.pointer.x, state.pointer.y, 0.5).unproject(state.camera);
      dir.copy(vec).sub(state.camera.position).normalize();
      vec.add(dir.multiplyScalar(state.camera.position.length()));
      [card, j1, j2, j3, fixed].forEach((ref) => ref.current?.wakeUp());
      card.current?.setNextKinematicTranslation({
        x: vec.x - dragged.x,
        y: vec.y - dragged.y,
        z: vec.z - dragged.z,
      });
    }
    if (fixed.current) {
      [j1, j2].forEach((ref) => {
        if (!ref.current.lerped) ref.current.lerped = new THREE.Vector3().copy(ref.current.translation());
        const clampedDistance = Math.max(0.1, Math.min(1, ref.current.lerped.distanceTo(ref.current.translation())));
        ref.current.lerped.lerp(
          ref.current.translation(),
          delta * (minSpeed + clampedDistance * (maxSpeed - minSpeed)),
        );
      });
      curve.points[0].copy(j3.current.translation());
      curve.points[1].copy(j2.current.lerped);
      curve.points[2].copy(j1.current.lerped);
      curve.points[3].copy(fixed.current.translation());
      band.current.geometry.setPoints(curve.getPoints(isMobile ? 16 : 32));
      ang.copy(card.current.angvel());
      rot.copy(card.current.rotation());
      card.current.setAngvel({ x: ang.x, y: ang.y - rot.y * 0.25, z: ang.z });
    }
  });

  curve.curveType = "chordal";
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping;

  return (
    <>
      <group position={[0, 7, 0]}>
        <RigidBody ref={fixed} {...segmentProps} type={"fixed" as RigidBodyProps["type"]} />
        <RigidBody position={[0.5, 0, 0]} ref={j1} {...segmentProps} type={"dynamic" as RigidBodyProps["type"]}>
          <BallCollider args={[0.1]} />
        </RigidBody>
        <RigidBody position={[1, 0, 0]} ref={j2} {...segmentProps} type={"dynamic" as RigidBodyProps["type"]}>
          <BallCollider args={[0.1]} />
        </RigidBody>
        <RigidBody position={[1.5, 0, 0]} ref={j3} {...segmentProps} type={"dynamic" as RigidBodyProps["type"]}>
          <BallCollider args={[0.1]} />
        </RigidBody>
        <RigidBody
          position={[1.7, -2.2, 0]}
          ref={card}
          {...segmentProps}
          type={dragged ? ("kinematicPosition" as RigidBodyProps["type"]) : ("dynamic" as RigidBodyProps["type"])}
        >
          <CuboidCollider args={[0.8, 1.125, 0.01]} />
          <group
            scale={3.9}
            position={[0, -2.7, -0.06]}
            onPointerOver={() => hover(true)}
            onPointerOut={() => hover(false)}
            onPointerUp={(e: any) => {
              e.target.releasePointerCapture(e.pointerId);
              drag(false);
            }}
            onPointerDown={(e: any) => {
              e.target.setPointerCapture(e.pointerId);
              drag(new THREE.Vector3().copy(e.point).sub(vec.copy(card.current.translation())));
            }}
          >
            <mesh geometry={nodes.card.geometry}>
              <meshPhysicalMaterial
                map={badgeTexture}
                side={THREE.FrontSide}
                map-anisotropy={16}
                clearcoat={isMobile ? 0 : 1}
                clearcoatRoughness={0.15}
                roughness={0.9}
                metalness={0.8}
              />
            </mesh>
            <mesh geometry={nodes.card.geometry} position={[0, 0, -0.02]}>
              <meshPhysicalMaterial
                map={badgeBackTexture}
                side={THREE.BackSide}
                map-anisotropy={16}
                clearcoat={isMobile ? 0 : 1}
                clearcoatRoughness={0.15}
                roughness={0.9}
                metalness={0.8}
              />
            </mesh>
            <mesh geometry={nodes.clip.geometry} material={materials.metal} material-roughness={0.3} />
            <mesh geometry={nodes.clamp.geometry} material={materials.metal} />
          </group>
        </RigidBody>
      </group>
      <mesh ref={band}>
        <meshLineGeometry />
        <meshLineMaterial
          color="white"
          depthTest={false}
          resolution={isMobile ? [1000, 2000] : [1000, 1000]}
          useMap
          map={texture}
          repeat={[-4, 1]}
          lineWidth={0.6}
        />
      </mesh>
    </>
  );
}

useGLTF.preload(cardGLB);
