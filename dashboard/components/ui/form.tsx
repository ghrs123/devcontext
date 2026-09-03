import * as React from 'react';
import {
  Controller,
  type ControllerProps,
  FormProvider,
  useFormContext,
  type FieldPath,
  type FieldValues
} from 'react-hook-form';

import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

const Form = FormProvider;

type FormFieldContextValue<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>> = {
  name: TName;
};

const FormFieldContext = React.createContext<FormFieldContextValue>({} as FormFieldContextValue);

const FormField = <
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>
>({
  ...props
}: ControllerProps<TFieldValues, TName>) => {
  return (
    <FormFieldContext.Provider value={{ name: props.name }}>
      <Controller {...props} />
    </FormFieldContext.Provider>
  );
};

const FormItem = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('space-y-2', className)} {...props} />
));
FormItem.displayName = 'FormItem';

const FormLabel = React.forwardRef<HTMLLabelElement, React.ComponentPropsWithoutRef<typeof Label>>(
  ({ className, ...props }, ref) => {
    const { formState } = useFormContext();
    const fieldContext = React.useContext(FormFieldContext);
    const error = fieldContext.name ? formState.errors[fieldContext.name] : undefined;

    return <Label ref={ref} className={cn(error && 'text-danger', className)} {...props} />;
  }
);
FormLabel.displayName = 'FormLabel';

const FormControl = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ ...props }, ref) => (
  <div ref={ref} {...props} />
));
FormControl.displayName = 'FormControl';

const FormMessage = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, children, ...props }, ref) => {
    const { getFieldState, formState } = useFormContext();
    const fieldContext = React.useContext(FormFieldContext);
    const fieldState = fieldContext.name ? getFieldState(fieldContext.name, formState) : undefined;
    const body = fieldState?.error?.message ?? children;

    if (!body) {
      return null;
    }

    return (
      <p ref={ref} className={cn('text-sm font-medium text-danger', className)} {...props}>
        {String(body)}
      </p>
    );
  }
);
FormMessage.displayName = 'FormMessage';

const FormDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => <p ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
);
FormDescription.displayName = 'FormDescription';

export { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage };
