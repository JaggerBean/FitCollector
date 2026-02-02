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
  position = [0, 0, 22],
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
    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 1536;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.fillStyle = "#f2f3f6";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const cardW = canvas.width * 0.84;
    const cardH = canvas.height * 0.86;
    const cardX = (canvas.width - cardW) / 2;
    const cardY = canvas.height * 0.08;

    ctx.save();
    ctx.beginPath();
    roundRect(ctx, cardX, cardY, cardW, cardH, 80);
    ctx.clip();

    const logo = logoTexture.image as HTMLImageElement;
    const maxLogoW = cardW * 0.5;
    const maxLogoH = cardH * 0.28;
    const logoScale = Math.min(maxLogoW / logo.width, maxLogoH / logo.height);
    const logoW = logo.width * logoScale;
    const logoH = logo.height * logoScale;
    const logoX = cardX + (cardW - logoW) / 2;
    const logoY = cardY + cardH * 0.14;
    ctx.drawImage(logo, logoX, logoY, logoW, logoH);

    ctx.fillStyle = "#0b1220";
    ctx.font = "bold 52px 'Segoe UI', Arial, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("StepCraft", cardX + cardW / 2, cardY + cardH * 0.42);

    const left = cardX + cardW * 0.08;
    const right = cardX + cardW * 0.92;
    let y = cardY + cardH * 0.52;

    const label = (text: string) => {
      ctx.fillStyle = "#2f5b4a";
      ctx.font = "700 26px 'Segoe UI', Arial, sans-serif";
      ctx.textAlign = "left";
      ctx.fillText(text, left, y);
      y += 36;
    };

    const value = (text: string) => {
      ctx.fillStyle = "#0f1b2b";
      ctx.font = "500 28px 'Segoe UI', Arial, sans-serif";
      ctx.textAlign = "left";
      const lines = wrapText(ctx, text, right - left);
      for (const line of lines) {
        ctx.fillText(line, left, y);
        y += 36;
      }
      y += 14;
    };

    label("SERVER");
    value(cardData.serverName);

    label("API KEY");
    value(cardData.apiKey);

    if (cardData.inviteCode) {
      label("INVITE CODE");
      value(cardData.inviteCode);
    }

    ctx.fillStyle = "#2b3a4a";
    ctx.font = "400 24px 'Segoe UI', Arial, sans-serif";
    ctx.textAlign = "left";
    const msg1 = "Use this key in the StepCraft Minecraft mod configuration.";
    const msg2 = `Email sent to ${cardData.ownerEmail}. Check spam if needed.`;
    for (const line of wrapText(ctx, msg1, right - left)) {
      ctx.fillText(line, left, y);
      y += 30;
    }
    y += 8;
    for (const line of wrapText(ctx, msg2, right - left)) {
      ctx.fillText(line, left, y);
      y += 30;
    }

    ctx.restore();

    badgeTexture.image = canvas;
    badgeTexture.needsUpdate = true;
    badgeTexture.flipY = false;
    badgeTexture.colorSpace = THREE.SRGBColorSpace;
  }, [cardData, logoTexture, badgeTexture]);

  function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
    const words = text.split(" ");
    const lines: string[] = [];
    let line = "";
    for (const word of words) {
      const test = line ? `${line} ${word}` : word;
      if (ctx.measureText(test).width > maxWidth && line) {
        lines.push(line);
        line = word;
      } else {
        line = test;
      }
    }
    if (line) lines.push(line);
    return lines;
  }

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

  useRopeJoint(fixed, j1, [[0, 0, 0], [0, 0, 0], 1]);
  useRopeJoint(j1, j2, [[0, 0, 0], [0, 0, 0], 1]);
  useRopeJoint(j2, j3, [[0, 0, 0], [0, 0, 0], 1]);
  useSphericalJoint(j3, card, [
    [0, 0, 0],
    [0, 1.6, 0],
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
      <group position={[0, 6, 0]}>
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
          position={[1.7, -0.45, 0]}
          ref={card}
          {...segmentProps}
          type={dragged ? ("kinematicPosition" as RigidBodyProps["type"]) : ("dynamic" as RigidBodyProps["type"])}
        >
          <CuboidCollider args={[0.8, 1.125, 0.01]} />
          <group
            scale={3.4}
            position={[0, -1.35, -0.06]}
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
              <meshStandardMaterial color="#f2f3f6" side={THREE.BackSide} />
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
