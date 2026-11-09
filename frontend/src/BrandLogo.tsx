import React from 'react';

export const SAUMYA_SOLAR_LOGO_URL='/images/soumya-solar-brand.svg';

type Props={className?:string;alt?:string;onClick?:()=>void;href?:string};
export default function BrandLogo({className='brand-logo',alt='Soumya Solar Energy Solution',onClick,href}:Props){
 const image=<img className={className} src={SAUMYA_SOLAR_LOGO_URL} alt={alt}/>;
 if(href)return <a className="brand-logo-link" href={href} aria-label={alt}>{image}</a>;
 if(onClick)return <button type="button" className="brand-logo-button" onClick={onClick} aria-label={alt}>{image}</button>;
 return image;
}
