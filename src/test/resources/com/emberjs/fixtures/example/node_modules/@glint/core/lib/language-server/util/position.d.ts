/**
 * The LSP refers to line/character combinations as positions,
 * while TS uses numeric offsets instead.
 */
export declare type Position = {
    line: number;
    character: number;
};
export declare function positionToOffset(contents: string, { line, character }: Position): number;
export declare function offsetToPosition(contents: string, position: number): Position;
export declare function computeLineStarts(text: string): number[];
