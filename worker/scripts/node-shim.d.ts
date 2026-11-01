// Minimal Node typings for the local scripts (the project deliberately has no @types/node dependency).
declare module "node:fs/promises" {
  export function readFile(path: string): Promise<Uint8Array>;
  export function readFile(path: string, encoding: "utf8"): Promise<string>;
  export function writeFile(path: string, data: string): Promise<void>;
  export function mkdir(path: string, opts?: { recursive?: boolean }): Promise<string | undefined>;
  export function access(path: string): Promise<void>;
}
declare module "node:path" {
  export function resolve(...parts: string[]): string;
  export function dirname(p: string): string;
  export function join(...parts: string[]): string;
  export function extname(p: string): string;
}
declare module "node:url" {
  export function fileURLToPath(url: string | URL): string;
}
declare const process: {
  argv: string[];
  env: Record<string, string | undefined>;
  exitCode: number | undefined;
  exit(code?: number): never;
};
interface ImportMeta {
  url: string;
}
